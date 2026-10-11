package com.mobilerepair.stock.org;

/**
 * A business-rule violation raised by {@link OrgStateService} (for example
 * "not enough stock" or a duplicate item id). Mapped to HTTP 400 by
 * {@code ApiExceptionHandler} so the browser can surface the message to the
 * user — mirroring the {@code throw new Error(...)} messages in app.js.
 */
public class OrgException extends RuntimeException {

    public OrgException(String message) {
        super(message);
    }
}
