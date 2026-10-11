package com.mobilerepair.stock.model;

import java.time.Instant;
import java.util.List;

public record StockItem(
        String id,
        String name,
        String category,
        String unit,
        int stock,
        int minStock,
        double costPrice,
        double sellingPrice,
        String condition,
        List<StockEvent> history,
        Instant updatedAt) {

    public boolean isLowStock() {
        return stock <= minStock;
    }

    public StockItem withStock(int newStock, StockEvent event) {
        List<StockEvent> next = history == null
                ? List.of(event)
                : java.util.stream.Stream.concat(history.stream(), java.util.stream.Stream.of(event)).toList();
        return new StockItem(id, name, category, unit, newStock, minStock,
                costPrice, sellingPrice, condition, next, Instant.now());
    }
}
