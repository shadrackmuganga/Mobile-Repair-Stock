package com.mobilerepair.stock.model;

import java.time.Instant;

public record Organisation(
        String orgId,
        String orgName,
        Instant createdAt) {
}
