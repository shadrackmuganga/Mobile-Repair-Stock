package com.mobilerepair.stock.org;

import com.mobilerepair.stock.realtime.DocStreamService;
import com.mobilerepair.stock.store.InMemoryDocStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrgStateServiceTest {

    private static final String ORG = "test-org";

    private final OrgStateService service = new OrgStateService(new InMemoryDocStore(), new DocStreamService());

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> state) {
        return (List<Map<String, Object>>) state.get("items");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> transactions(Map<String, Object> state) {
        return (List<Map<String, Object>>) state.get("transactions");
    }

    private Map<String, Object> find(List<Map<String, Object>> list, String key, String value) {
        return list.stream().filter(m -> value.equals(String.valueOf(m.get(key)))).findFirst().orElse(null);
    }

    @Test
    void seedsDefaultInventory() {
        Map<String, Object> state = service.state(ORG);
        assertEquals(3, items(state).size());
        assertEquals("iPhone 12 LCD", find(items(state), "id", "lcd-iphone-12").get("name"));
    }

    @Test
    void addItemRejectsDuplicates() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "screen-1");
        item.put("name", "Screen");
        item.put("stock", 4);
        item.put("minStock", 2);
        item.put("costPrice", 10);
        item.put("sellingPrice", 20);
        service.addItem(ORG, item, "admin");

        OrgException error = assertThrows(OrgException.class, () -> service.addItem(ORG, item, "admin"));
        assertTrue(error.getMessage().contains("already exists"));
    }

    @Test
    void stockInAndOutAdjustStockAndLogTransactions() {
        Map<String, Object> state = service.state(ORG);
        assertEquals(8, ((Number) find(items(state), "id", "lcd-iphone-12").get("stock")).intValue());

        service.stockIn(ORG, "lcd-iphone-12", 5, "Delivery", "admin");
        Map<String, Object> after = service.state(ORG);
        assertEquals(13, ((Number) find(items(after), "id", "lcd-iphone-12").get("stock")).intValue());

        service.stockOut(ORG, "lcd-iphone-12", 3, "Repair used", "admin");
        Map<String, Object> outState = service.state(ORG);
        assertEquals(10, ((Number) find(items(outState), "id", "lcd-iphone-12").get("stock")).intValue());
        assertEquals(2, transactions(outState).size());
        assertEquals("out", transactions(outState).get(1).get("type"));
    }

    @Test
    void saleComputesRevenueAndProfit() {
        Map<String, Object> response = service.sale(ORG, "lcd-iphone-12", 2, "Walk-in", "Sold to customer", "admin");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertEquals(120, ((Number) result.get("revenue")).intValue());
        assertEquals(50, ((Number) result.get("profit")).intValue());
        assertEquals(6, ((Number) result.get("stockAfter")).intValue());
    }

    @Test
    void saleRejectsInsufficientStock() {
        OrgException error = assertThrows(OrgException.class,
                () -> service.sale(ORG, "battery-samsung-a52", 99, "Walk-in", "Sold", "admin"));
        assertTrue(error.getMessage().startsWith("Not enough stock"));
    }

    @Test
    void deleteItemTracksTombstoneAndRemovesTicket() {
        Map<String, Object> ticketResponse = service.createRepair(ORG, repairPayload("lcd-iphone-12"), "admin");
        @SuppressWarnings("unchecked")
        Map<String, Object> ticket = (Map<String, Object>) ticketResponse.get("result");
        String ticketId = String.valueOf(ticket.get("ticketId"));

        Map<String, Object> response = service.deleteItem(ORG, "lcd-iphone-12", "admin");
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) response.get("state");
        assertTrue(state.get("deletedItems") instanceof Map<?, ?> tombstones && tombstones.containsKey("lcd-iphone-12"));
        assertEquals(0, ((List<?>) state.get("repairTickets")).size());
        assertFalse(items(state).stream().anyMatch(i -> "lcd-iphone-12".equals(i.get("id"))));
        assertNotNull(ticketId);
    }

    @Test
    void repairLifecycle() {
        Map<String, Object> created = service.createRepair(ORG, repairPayload("battery-samsung-a52"), "admin");
        @SuppressWarnings("unchecked")
        Map<String, Object> ticket = (Map<String, Object>) created.get("result");
        assertEquals("Open", ticket.get("status"));
        String ticketId = String.valueOf(ticket.get("ticketId"));

        Map<String, Object> completed = service.completeRepair(ORG, ticketId, "admin");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) completed.get("result");
        assertEquals("Completed", result.get("status"));
    }

    @Test
    void phoneTradeBuyThenSell() {
        Map<String, Object> buy = new LinkedHashMap<>();
        buy.put("type", "buy");
        buy.put("brand", "Apple");
        buy.put("model", "iPhone 11");
        buy.put("condition", "Grade A");
        buy.put("price", 200);
        buy.put("qty", 3);
        service.phoneTrade(ORG, buy, "admin");

        Map<String, Object> afterBuy = service.state(ORG);
        Map<String, Object> phone = find(items(afterBuy), "id", "phone-apple-iphone-11");
        assertNotNull(phone);
        assertEquals(3, ((Number) phone.get("stock")).intValue());
        assertEquals("phone-buy", transactions(afterBuy).get(0).get("type"));

        Map<String, Object> sell = new LinkedHashMap<>(buy);
        sell.put("type", "sell");
        sell.put("price", 300);
        sell.put("qty", 1);
        Map<String, Object> sellResponse = service.phoneTrade(ORG, sell, "admin");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) sellResponse.get("result");
        assertEquals("sell", result.get("mode"));
        assertNotNull(result.get("receipt"));

        Map<String, Object> afterSell = service.state(ORG);
        assertEquals(2, ((Number) find(items(afterSell), "id", "phone-apple-iphone-11").get("stock")).intValue());
    }

    @Test
    void preferencesAndReset() {
        service.savePreferences(ORG, "KES", "sw", "admin");
        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) service.state(ORG).get("preferences");
        assertEquals("KES", prefs.get("currency"));
        assertEquals("sw", prefs.get("language"));

        service.addItem(ORG, repairItem("extra"), "admin");
        service.reset(ORG, "admin");
        Map<String, Object> state = service.state(ORG);
        assertEquals(3, items(state).size());
        assertEquals(0, transactions(state).size());
    }

    private Map<String, Object> repairPayload(String itemId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerName", "Jane Doe");
        payload.put("deviceModel", "iPhone 12");
        payload.put("itemId", itemId);
        payload.put("quantityUsed", 1);
        payload.put("problem", "Cracked screen");
        return payload;
    }

    private Map<String, Object> repairItem(String id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", "Extra");
        item.put("stock", 1);
        item.put("minStock", 1);
        return item;
    }
}
