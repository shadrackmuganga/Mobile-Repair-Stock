package com.mobilerepair.stock.inventory;

import com.mobilerepair.stock.model.StockEvent;
import com.mobilerepair.stock.model.StockItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final ItemRepository repository;

    public InventoryService(ItemRepository repository) {
        this.repository = repository;
    }

    public List<StockItem> list(String orgId, String query, String category) {
        return repository.findByOrg(orgId).stream()
                .filter(item -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(item.category()))
                .filter(item -> query == null || query.isBlank()
                        || (item.name() != null && item.name().toLowerCase().contains(query.toLowerCase())))
                .toList();
    }

    public StockItem get(String orgId, String itemId) {
        return repository.find(orgId, itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
    }

    public StockItem create(String orgId, StockItem item) {
        String id = (item.id() == null || item.id().isBlank()) ? UUID.randomUUID().toString() : item.id();
        StockItem toSave = new StockItem(
                id,
                item.name(),
                item.category(),
                item.unit(),
                item.stock(),
                item.minStock(),
                item.costPrice(),
                item.sellingPrice(),
                item.condition(),
                item.history() == null ? List.of() : item.history(),
                Instant.now());
        return repository.save(orgId, toSave);
    }

    public StockItem update(String orgId, String itemId, StockItem item) {
        StockItem toSave = new StockItem(
                itemId,
                item.name(),
                item.category(),
                item.unit(),
                item.stock(),
                item.minStock(),
                item.costPrice(),
                item.sellingPrice(),
                item.condition(),
                item.history() == null ? List.of() : item.history(),
                Instant.now());
        return repository.save(orgId, toSave);
    }

    public void delete(String orgId, String itemId) {
        repository.delete(orgId, itemId);
    }

    public StockItem adjustStock(String orgId, String itemId, int delta, String type, String reason) {
        StockItem item = get(orgId, itemId);
        int newStock = Math.max(0, item.stock() + delta);
        StockEvent event = new StockEvent(type, Math.abs(delta), reason, Instant.now());
        return repository.save(orgId, item.withStock(newStock, event));
    }
}
