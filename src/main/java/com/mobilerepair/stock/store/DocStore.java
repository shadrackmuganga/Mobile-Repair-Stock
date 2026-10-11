package com.mobilerepair.stock.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DocStore {

    List<Map<String, Object>> list(String collection);

    Optional<Map<String, Object>> get(String collection, String id);

    void put(String collection, String id, Map<String, Object> document);
}
