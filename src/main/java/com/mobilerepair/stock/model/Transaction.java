package com.mobilerepair.stock.model;

import java.time.Instant;

public record Transaction(
        String txnId,
        Instant date,
        String createdBy,
        String type,
        double amount,
        String reason,
        String customer,
        int stockBefore,
        int stockAfter) {
}
