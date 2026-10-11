package com.mobilerepair.stock.model;

public record UserProfile(
        String uid,
        String email,
        String fullName,
        String contact,
        String role,
        String orgId) {
}
