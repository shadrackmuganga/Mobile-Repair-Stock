package com.mobilerepair.stock.model;

import java.time.Instant;

public record RepairTicket(
        String ticketId,
        String customerName,
        String deviceModel,
        String itemId,
        int quantityUsed,
        String problem,
        String status,
        Instant date) {
}
