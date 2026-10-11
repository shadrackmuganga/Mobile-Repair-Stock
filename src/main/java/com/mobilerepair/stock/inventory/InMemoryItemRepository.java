package com.mobilerepair.stock.inventory;

import com.mobilerepair.stock.model.StockItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryItemRepository implements ItemRepository {

    private final Map<String, Map<String, StockItem>> byOrg = new ConcurrentHashMap<>();

    @Override
    public List<StockItem> findByOrg(String orgId) {
        return List.copyOf(items(orgId).values());
    }

    @Override
    public Optional<StockItem> find(String orgId, String itemId) {
        return Optional.ofNullable(items(orgId).get(itemId));
    }

    @Override
    public StockItem save(String orgId, StockItem item) {
        items(orgId).put(item.id(), item);
        return item;
    }

    @Override
    public void delete(String orgId, String itemId) {
        items(orgId).remove(itemId);
    }

    private Map<String, StockItem> items(String orgId) {
        return byOrg.computeIfAbsent(orgId, key -> new ConcurrentHashMap<>());
    }
}
