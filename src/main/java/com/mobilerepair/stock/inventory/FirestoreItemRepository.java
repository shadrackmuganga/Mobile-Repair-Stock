package com.mobilerepair.stock.inventory;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.mobilerepair.stock.model.StockEvent;
import com.mobilerepair.stock.model.StockItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestoreItemRepository implements ItemRepository {

    private static final String COLLECTION = "orgData";
    private static final String ITEMS = "items";

    private final Firestore firestore;

    public FirestoreItemRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public List<StockItem> findByOrg(String orgId) {
        try {
            List<StockItem> result = new ArrayList<>();
            for (QueryDocumentSnapshot doc : items(orgId).get().get().getDocuments()) {
                result.add(fromMap(doc.getId(), doc.getData()));
            }
            return result;
        } catch (Exception e) {
            throw new ItemStoreException("Unable to list items for org " + orgId, e);
        }
    }

    @Override
    public Optional<StockItem> find(String orgId, String itemId) {
        try {
            DocumentSnapshot doc = items(orgId).document(itemId).get().get();
            return doc.exists() ? Optional.of(fromMap(doc.getId(), doc.getData())) : Optional.empty();
        } catch (Exception e) {
            throw new ItemStoreException("Unable to read item " + itemId, e);
        }
    }

    @Override
    public StockItem save(String orgId, StockItem item) {
        try {
            items(orgId).document(item.id()).set(toMap(item)).get();
            return item;
        } catch (Exception e) {
            throw new ItemStoreException("Unable to save item " + item.id(), e);
        }
    }

    @Override
    public void delete(String orgId, String itemId) {
        try {
            items(orgId).document(itemId).delete().get();
        } catch (Exception e) {
            throw new ItemStoreException("Unable to delete item " + itemId, e);
        }
    }

    private CollectionReference items(String orgId) {
        return firestore.collection(COLLECTION).document(orgId).collection(ITEMS);
    }

    private Map<String, Object> toMap(StockItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.id());
        map.put("name", item.name());
        map.put("category", item.category());
        map.put("unit", item.unit());
        map.put("stock", item.stock());
        map.put("minStock", item.minStock());
        map.put("costPrice", item.costPrice());
        map.put("sellingPrice", item.sellingPrice());
        map.put("condition", item.condition());
        map.put("updatedAt", item.updatedAt() == null ? null : item.updatedAt().toString());
        List<Map<String, Object>> history = new ArrayList<>();
        if (item.history() != null) {
            for (StockEvent event : item.history()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("type", event.type());
                entry.put("amount", event.amount());
                entry.put("reason", event.reason());
                entry.put("date", event.date() == null ? null : event.date().toString());
                history.add(entry);
            }
        }
        map.put("history", history);
        return map;
    }

    private StockItem fromMap(String id, Map<String, Object> data) {
        Map<String, Object> safe = data == null ? Map.of() : data;
        return new StockItem(
                id,
                str(safe.get("name")),
                str(safe.get("category")),
                str(safe.get("unit")),
                intOf(safe.get("stock")),
                intOf(safe.get("minStock")),
                dbl(safe.get("costPrice")),
                dbl(safe.get("sellingPrice")),
                str(safe.get("condition")),
                historyOf(safe.get("history")),
                instant(safe.get("updatedAt")));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double dbl(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private static Instant instant(Object value) {
        return value == null ? null : Instant.parse(value.toString());
    }

    private static List<StockEvent> historyOf(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<StockEvent> events = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                events.add(new StockEvent(
                        str(map.get("type")),
                        intOf(map.get("amount")),
                        str(map.get("reason")),
                        instant(map.get("date"))));
            }
        }
        return events;
    }
}
