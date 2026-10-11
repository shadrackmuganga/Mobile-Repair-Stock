package com.mobilerepair.stock.org;

import com.mobilerepair.stock.realtime.DocStreamService;
import com.mobilerepair.stock.store.DocStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side port of the browser's {@code MobileRepairStockSystem} /
 * {@code StockItem} domain logic in app.js. Every organisation keeps its whole
 * state in a single {@code orgData/{orgId}} document (items, transactions,
 * repair tickets, deleted-item tombstones, preferences) exactly as the
 * Firestore schema already does.
 *
 * <p>Mutating operations run under a per-organisation lock, persist the
 * authoritative document, then broadcast it over SSE so every connected device
 * refreshes — replacing the client-side business rules that used to run in
 * app.js.
 */
@Service
public class OrgStateService {

    public static final String COLLECTION = "orgData";

    private static final String DEFAULT_CURRENCY = "USD";
    private static final String DEFAULT_LANGUAGE = "en";

    private final DocStore store;
    private final DocStreamService streams;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public OrgStateService(DocStore store, DocStreamService streams) {
        this.store = store;
        this.streams = streams;
    }

    @FunctionalInterface
    private interface Operation {
        Object apply(Map<String, Object> state, String user);
    }

    // ---- reads ------------------------------------------------------------

    public Map<String, Object> state(String orgId) {
        return current(orgId);
    }

    public Map<String, Object> reportSummary(String orgId) {
        Map<String, Object> state = current(orgId);
        List<Map<String, Object>> items = items(state);
        List<Map<String, Object>> transactions = transactions(state);

        double inventoryValue = 0;
        int lowStock = 0;
        for (Map<String, Object> item : items) {
            inventoryValue += num(item.get("stock")) * num(item.get("costPrice"));
            if (num(item.get("stock")) <= num(item.get("minStock"))) lowStock++;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("inventoryValue", number(inventoryValue));
        summary.put("itemCount", items.size());
        summary.put("lowStockItems", lowStock);
        summary.put("openTickets", openTickets(state).size());
        summary.put("totalSalesRevenue", number(sumByType(transactions, "sale", "revenue")));
        summary.put("totalSalesProfit", number(sumByType(transactions, "sale", "profit")));
        summary.put("phoneSalesRevenue", number(sumByType(transactions, "phone-sale", "revenue")));
        summary.put("phoneSalesProfit", number(sumByType(transactions, "phone-sale", "profit")));
        return summary;
    }

    // ---- operations -------------------------------------------------------

    public Map<String, Object> addItem(String orgId, Map<String, Object> itemData, String user) {
        return mutate(orgId, user, (state, u) -> {
            Map<String, Object> item = buildNewItem(itemData);
            if (findItem(items(state), str(item.get("id"))) != null) {
                throw new OrgException("Item with id " + item.get("id") + " already exists");
            }
            items(state).add(item);
            return item;
        });
    }

    public Map<String, Object> deleteItem(String orgId, String id, String user) {
        return mutate(orgId, user, (state, u) -> {
            Map<String, Object> item = requireItem(state, id);
            items(state).remove(item);
            deletedItems(state).put(id, now());
            logTransaction(state, u, entry(
                    "id", id, "name", item.get("name"), "type", "deleted", "amount", number(0),
                    "reason", "Item removed from inventory", "customer", "Admin",
                    "stockBefore", number(num(item.get("stock"))), "stockAfter", number(0)));
            repairTickets(state).removeIf(ticket -> id.equals(str(ticket.get("itemId"))));
            return item;
        });
    }

    public Map<String, Object> stockIn(String orgId, String id, double amount, String reason, String user) {
        return mutate(orgId, user, (state, u) -> {
            Map<String, Object> item = requireItem(state, id);
            String resolved = reason != null ? reason : "Supplier delivery";
            double before = num(item.get("stock"));
            double after = addStock(item, amount, resolved);
            logTransaction(state, u, entry(
                    "id", id, "name", item.get("name"), "type", "in", "amount", number(amount),
                    "reason", resolved, "customer", "Supplier",
                    "stockBefore", number(before), "stockAfter", number(after)));
            return number(after);
        });
    }

    public Map<String, Object> stockOut(String orgId, String id, double amount, String reason, String user) {
        return mutate(orgId, user, (state, u) -> {
            Map<String, Object> item = requireItem(state, id);
            String resolved = reason != null ? reason : "Repair used";
            double before = num(item.get("stock"));
            double after = removeStock(item, amount, resolved);
            logTransaction(state, u, entry(
                    "id", id, "name", item.get("name"), "type", "out", "amount", number(amount),
                    "reason", resolved, "customer", "Internal",
                    "stockBefore", number(before), "stockAfter", number(after)));
            return number(after);
        });
    }

    public Map<String, Object> sale(String orgId, String id, double amount, String customer, String reason, String user) {
        return mutate(orgId, user, (state, u) -> {
            Map<String, Object> item = requireItem(state, id);
            if (amount <= 0) throw new OrgException("Quantity must be greater than zero");
            double before = num(item.get("stock"));
            if (amount > before) throw new OrgException("Not enough stock for " + item.get("name"));

            item.put("stock", number(before - amount));
            item.put("updatedAt", now());

            double revenue = num(item.get("sellingPrice")) * amount;
            double profit = (num(item.get("sellingPrice")) - num(item.get("costPrice"))) * amount;

            logTransaction(state, u, entry(
                    "id", id, "name", item.get("name"), "type", "sale", "amount", number(amount),
                    "customer", customer != null ? customer : "Walk-in",
                    "reason", reason != null ? reason : "Sold to customer",
                    "stockBefore", number(before), "stockAfter", number(num(item.get("stock"))),
                    "revenue", number(revenue), "profit", number(profit)));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("stockAfter", number(num(item.get("stock"))));
            result.put("revenue", number(revenue));
            result.put("profit", number(profit));
            return result;
        });
    }

    public Map<String, Object> createRepair(String orgId, Map<String, Object> payload, String user) {
        return mutate(orgId, user, (state, u) -> {
            String itemId = str(payload.get("itemId"));
            Map<String, Object> item = requireItem(state, itemId);
            double qty = num(payload.get("quantityUsed"));
            if (qty <= 0) throw new OrgException("Quantity must be greater than zero");
            if (qty > num(item.get("stock"))) throw new OrgException("Not enough stock for " + item.get("name"));

            String ticketId = "RT-" + System.currentTimeMillis() + "-" + randHex(4);
            Map<String, Object> ticket = new LinkedHashMap<>();
            ticket.put("ticketId", ticketId);
            ticket.put("customerName", str(payload.get("customerName")));
            ticket.put("deviceModel", str(payload.get("deviceModel")));
            ticket.put("itemId", itemId);
            ticket.put("quantityUsed", number(qty));
            ticket.put("problem", str(payload.get("problem")));
            ticket.put("status", "Open");
            ticket.put("date", now());

            double before = num(item.get("stock"));
            double after = removeStock(item, qty, "Repair use");
            logTransaction(state, u, entry(
                    "id", itemId, "name", item.get("name"), "type", "repair-use", "amount", number(qty),
                    "customer", ticket.get("customerName"), "reason", "Repair ticket " + ticketId,
                    "stockBefore", number(before), "stockAfter", number(after)));

            repairTickets(state).add(ticket);
            return ticket;
        });
    }

    public Map<String, Object> completeRepair(String orgId, String ticketId, String user) {
        return mutate(orgId, user, (state, u) -> {
            Map<String, Object> ticket = repairTickets(state).stream()
                    .filter(t -> ticketId.equals(str(t.get("ticketId"))))
                    .findFirst()
                    .orElseThrow(() -> new OrgException("Repair ticket not found"));
            ticket.put("status", "Completed");
            return ticket;
        });
    }

    public Map<String, Object> phoneTrade(String orgId, Map<String, Object> payload, String user) {
        return mutate(orgId, user, (state, u) -> {
            String type = str(payload.get("type"));
            String brand = str(payload.get("brand"));
            String model = str(payload.get("model"));
            String condition = blank(payload.get("condition")) ? "Grade A" : str(payload.get("condition"));
            double price = num(payload.get("price"));
            double qty = num(payload.get("qty"));

            if (brand.isBlank() || model.isBlank() || qty <= 0 || price <= 0) {
                throw new OrgException("Please complete the brand, model, quantity, and price fields.");
            }

            String phoneId = "phone-" + phoneSlug(brand) + "-" + phoneSlug(model);
            Map<String, Object> item = findItem(items(state), phoneId);

            if (item == null && "buy".equals(type)) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", phoneId);
                data.put("name", brand + " " + model);
                data.put("category", "Phone");
                data.put("unit", "piece");
                data.put("stock", 0);
                data.put("minStock", 1);
                data.put("costPrice", price);
                data.put("sellingPrice", price * 1.5);
                data.put("condition", condition);
                item = buildNewItem(data);
                items(state).add(item);
            }

            if (item == null) {
                throw new OrgException("This phone is not in stock yet. Buy it first.");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            if ("buy".equals(type)) {
                double before = num(item.get("stock"));
                double after = addStock(item, qty, "Phone purchase - " + condition);
                logTransaction(state, u, entry(
                        "id", phoneId, "name", item.get("name"), "type", "phone-buy", "amount", number(qty),
                        "reason", "Phone purchase - " + condition, "customer", "Supplier",
                        "stockBefore", number(before), "stockAfter", number(after), "costPrice", number(price)));
                item.put("costPrice", number(price));
                item.put("sellingPrice", number(Math.max(price * 1.5, price + 50)));
                item.put("condition", condition);
                item.put("updatedAt", now());
                result.put("mode", "buy");
                result.put("item", brand + " " + model);
                result.put("qty", number(qty));
            } else {
                if (qty > num(item.get("stock"))) {
                    throw new OrgException("Not enough phone stock. Available: " + fmt(num(item.get("stock"))));
                }
                double before = num(item.get("stock"));
                double after = removeStock(item, qty, "Phone sale - " + condition);
                double revenue = qty * price;
                double profit = qty * (price - num(item.get("costPrice")));
                logTransaction(state, u, entry(
                        "id", phoneId, "name", item.get("name"), "type", "phone-sale", "amount", number(qty),
                        "customer", "Walk-in", "reason", "Phone sale - " + condition,
                        "stockBefore", number(before), "stockAfter", number(after),
                        "revenue", number(revenue), "profit", number(profit)));
                item.put("condition", condition);
                item.put("updatedAt", now());

                Map<String, Object> receipt = new LinkedHashMap<>();
                receipt.put("title", "Phone Sale Receipt");
                receipt.put("item", brand + " " + model);
                receipt.put("condition", condition);
                receipt.put("quantity", number(qty));
                receipt.put("price", number(price));
                receipt.put("total", number(qty * price));
                receipt.put("date", now());
                receipt.put("transactionId", "PHONE-" + System.currentTimeMillis());

                result.put("mode", "sell");
                result.put("receipt", receipt);
            }
            return result;
        });
    }

    public Map<String, Object> savePreferences(String orgId, String currency, String language, String user) {
        return mutate(orgId, user, (state, u) -> {
            Map<String, Object> prefs = preferences(state);
            if (currency != null) prefs.put("currency", currency);
            if (language != null) prefs.put("language", language);
            return new LinkedHashMap<>(prefs);
        });
    }

    public Map<String, Object> reset(String orgId, String user) {
        return mutate(orgId, user, (state, u) -> {
            Map<String, Object> fresh = defaults();
            state.clear();
            state.putAll(fresh);
            return null;
        });
    }

    // ---- mutation plumbing ------------------------------------------------

    private Map<String, Object> mutate(String orgId, String user, Operation op) {
        Object lock = locks.computeIfAbsent(orgId, key -> new Object());
        synchronized (lock) {
            Map<String, Object> state = current(orgId);
            Object result = op.apply(state, user);
            state.put("updatedAt", now());
            store.put(COLLECTION, orgId, state);
            streams.publish(COLLECTION, orgId, state);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("state", state);
            out.put("result", result);
            return out;
        }
    }

    private Map<String, Object> current(String orgId) {
        Map<String, Object> doc = store.get(COLLECTION, orgId).orElse(null);
        if (doc == null) {
            Map<String, Object> seeded = defaults();
            store.put(COLLECTION, orgId, seeded);
            return seeded;
        }
        return normalize(doc);
    }

    private Map<String, Object> normalize(Map<String, Object> doc) {
        Map<String, Object> state = new LinkedHashMap<>(doc);
        state.putIfAbsent("items", new ArrayList<Map<String, Object>>());
        state.putIfAbsent("transactions", new ArrayList<Map<String, Object>>());
        state.putIfAbsent("repairTickets", new ArrayList<Map<String, Object>>());
        state.putIfAbsent("deletedItems", new LinkedHashMap<String, Object>());

        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("currency", DEFAULT_CURRENCY);
        prefs.put("language", DEFAULT_LANGUAGE);
        if (state.get("preferences") instanceof Map<?, ?> existing) {
            existing.forEach((k, v) -> prefs.put(String.valueOf(k), v));
        }
        state.put("preferences", prefs);
        state.putIfAbsent("updatedAt", now());
        return state;
    }

    private Map<String, Object> defaults() {
        Map<String, Object> state = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(defaultItem("lcd-iphone-12", "iPhone 12 LCD", "Display", "piece", 8, 5, 35, 60));
        items.add(defaultItem("battery-samsung-a52", "Samsung A52 Battery", "Battery", "piece", 3, 4, 18, 32));
        items.add(defaultItem("charging-port-xiaomi-redmi", "Xiaomi Redmi Charging Port", "Charging", "piece", 12, 6, 12, 25));
        state.put("items", items);
        state.put("transactions", new ArrayList<Map<String, Object>>());
        state.put("repairTickets", new ArrayList<Map<String, Object>>());
        state.put("deletedItems", new LinkedHashMap<String, Object>());
        Map<String, Object> prefs = new LinkedHashMap<>();
        prefs.put("currency", DEFAULT_CURRENCY);
        prefs.put("language", DEFAULT_LANGUAGE);
        state.put("preferences", prefs);
        state.put("updatedAt", now());
        return state;
    }

    private Map<String, Object> defaultItem(String id, String name, String category, String unit,
                                            double stock, double minStock, double cost, double selling) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("category", category);
        item.put("unit", unit);
        item.put("stock", number(stock));
        item.put("minStock", number(minStock));
        item.put("costPrice", number(cost));
        item.put("sellingPrice", number(selling));
        return item;
    }

    private Map<String, Object> buildNewItem(Map<String, Object> itemData) {
        String id = str(itemData.get("id"));
        String name = str(itemData.get("name"));
        if (id.isBlank() || name.isBlank()) {
            throw new OrgException("Item id and name are required");
        }
        double stock = num(itemData.get("stock"));
        double minStock = num(itemData.get("minStock"));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("category", blank(itemData.get("category")) ? "General" : str(itemData.get("category")));
        item.put("unit", blank(itemData.get("unit")) ? "piece" : str(itemData.get("unit")));
        item.put("stock", number(stock));
        item.put("minStock", number(minStock));
        item.put("costPrice", number(num(itemData.get("costPrice"))));
        item.put("sellingPrice", number(num(itemData.get("sellingPrice"))));
        item.put("condition", str(itemData.get("condition")));
        item.put("isLowStock", stock <= minStock);
        item.put("history", new ArrayList<Map<String, Object>>());
        item.put("updatedAt", now());
        return item;
    }

    private double addStock(Map<String, Object> item, double amount, String reason) {
        if (amount <= 0) throw new OrgException("Quantity must be greater than zero");
        double before = num(item.get("stock"));
        double after = before + amount;
        item.put("stock", number(after));
        history(item).add(entry("type", "in", "amount", number(amount), "reason", reason, "date", now()));
        item.put("updatedAt", now());
        return after;
    }

    private double removeStock(Map<String, Object> item, double amount, String reason) {
        if (amount <= 0) throw new OrgException("Quantity must be greater than zero");
        double before = num(item.get("stock"));
        if (amount > before) throw new OrgException("Not enough stock for " + item.get("name"));
        double after = before - amount;
        item.put("stock", number(after));
        history(item).add(entry("type", "out", "amount", number(amount), "reason", reason, "date", now()));
        item.put("updatedAt", now());
        return after;
    }

    private void logTransaction(Map<String, Object> state, String user, Map<String, Object> entry) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("txnId", "TXN-" + System.currentTimeMillis() + "-" + randHex(6));
        record.put("date", now());
        record.put("createdBy", (user == null || user.isBlank()) ? "system" : user);
        record.putAll(entry);
        transactions(state).add(record);
    }

    // ---- tiny helpers -----------------------------------------------------

    private Map<String, Object> requireItem(Map<String, Object> state, String id) {
        Map<String, Object> item = findItem(items(state), id);
        if (item == null) throw new OrgException("Item not found");
        return item;
    }

    private Map<String, Object> findItem(List<Map<String, Object>> items, String id) {
        for (Map<String, Object> item : items) {
            if (id != null && id.equals(str(item.get("id")))) return item;
        }
        return null;
    }

    private List<Map<String, Object>> openTickets(Map<String, Object> state) {
        List<Map<String, Object>> open = new ArrayList<>();
        for (Map<String, Object> ticket : repairTickets(state)) {
            if ("Open".equals(str(ticket.get("status")))) open.add(ticket);
        }
        return open;
    }

    private double sumByType(List<Map<String, Object>> transactions, String type, String field) {
        double total = 0;
        for (Map<String, Object> txn : transactions) {
            if (type.equals(str(txn.get("type")))) total += num(txn.get(field));
        }
        return total;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> state) {
        Object value = state.get("items");
        if (!(value instanceof List)) {
            value = new ArrayList<Map<String, Object>>();
            state.put("items", value);
        }
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> transactions(Map<String, Object> state) {
        Object value = state.get("transactions");
        if (!(value instanceof List)) {
            value = new ArrayList<Map<String, Object>>();
            state.put("transactions", value);
        }
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> repairTickets(Map<String, Object> state) {
        Object value = state.get("repairTickets");
        if (!(value instanceof List)) {
            value = new ArrayList<Map<String, Object>>();
            state.put("repairTickets", value);
        }
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deletedItems(Map<String, Object> state) {
        Object value = state.get("deletedItems");
        if (!(value instanceof Map)) {
            value = new LinkedHashMap<String, Object>();
            state.put("deletedItems", value);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> preferences(Map<String, Object> state) {
        Object value = state.get("preferences");
        if (!(value instanceof Map)) {
            value = new LinkedHashMap<String, Object>();
            state.put("preferences", value);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> history(Map<String, Object> item) {
        Object value = item.get("history");
        if (!(value instanceof List)) {
            value = new ArrayList<Map<String, Object>>();
            item.put("history", value);
        }
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> entry(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    private static String now() {
        return Instant.now().toString();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static double num(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Whole numbers stay integral (8 rather than 8.0) to match the JS output. */
    private static Object number(double value) {
        if (!Double.isInfinite(value) && !Double.isNaN(value) && value == Math.rint(value)) {
            return (long) value;
        }
        return value;
    }

    private static String fmt(double value) {
        return String.valueOf(number(value));
    }

    /** Mirrors the inline slug used for phone ids in app.js (no edge trimming). */
    private static String phoneSlug(String text) {
        return str(text).toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private static String randHex(int length) {
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (sb.length() < length) sb.append(Integer.toHexString(random.nextInt(16)));
        return sb.substring(0, length);
    }
}
