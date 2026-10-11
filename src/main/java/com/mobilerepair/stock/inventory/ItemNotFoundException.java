package com.mobilerepair.stock.inventory;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(String itemId) {
        super("Item not found: " + itemId);
    }
}
