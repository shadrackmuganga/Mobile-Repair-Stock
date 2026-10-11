package com.mobilerepair.stock.config;

import com.mobilerepair.stock.inventory.ItemNotFoundException;
import com.mobilerepair.stock.inventory.ItemStoreException;
import com.mobilerepair.stock.org.OrgException;
import com.mobilerepair.stock.store.DocStoreException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not-found", "message", e.getMessage()));
    }

    @ExceptionHandler(OrgException.class)
    public ResponseEntity<Map<String, Object>> handleOrg(OrgException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid-operation", "message", e.getMessage()));
    }

    @ExceptionHandler({ItemStoreException.class, DocStoreException.class})
    public ResponseEntity<Map<String, Object>> handleStore(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "storage", "message", e.getMessage()));
    }
}
