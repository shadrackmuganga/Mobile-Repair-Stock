package com.mobilerepair.stock.store;

public class DocStoreException extends RuntimeException {

    public DocStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocStoreException(String message) {
        super(message);
    }
}
