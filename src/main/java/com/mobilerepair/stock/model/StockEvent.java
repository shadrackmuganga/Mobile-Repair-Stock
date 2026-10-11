package com.mobilerepair.stock.model;

import java.time.Instant;

public record StockEvent(
        String type,
        int amount,
        String reason,
        Instant date) {
}
