package com.mobilerepair.stock.model;

public record Preferences(String currency, String language) {

    public static final Preferences DEFAULT = new Preferences("USD", "en");
}
