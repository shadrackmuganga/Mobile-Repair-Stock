package com.mobilerepair.stock.print;

/** Raised when the backend cannot reach or write to a printer. Mapped to HTTP 502. */
public class PrintException extends RuntimeException {
    public PrintException(String message) {
        super(message);
    }
}
