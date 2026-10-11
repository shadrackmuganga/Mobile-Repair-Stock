package com.mobilerepair.stock.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDocStore implements DocStore {

    private final Map<String, Map<String, Map<String, Object>>> collections = new ConcurrentHashMap<>();

    @Override
    public List<Map<String, Object>> list(String collection) {
        List<Map<String, Object>> result = new ArrayList<>();
        docs(collection).forEach((id, document) -> result.add(withId(id, document)));
        return result;
    }

    @Override
    public Optional<Map<String, Object>> get(String collection, String id) {
        Map<String, Object> document = docs(collection).get(id);
        return document == null ? Optional.empty() : Optional.of(withId(id, document));
    }

    @Override
    public void put(String collection, String id, Map<String, Object> document) {
        docs(collection).put(id, document);
    }

    private Map<String, Map<String, Object>> docs(String collection) {
        return collections.computeIfAbsent(collection, key -> new ConcurrentHashMap<>());
    }

    private Map<String, Object> withId(String id, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        if (data != null) {
            result.putAll(data);
        }
        return result;
    }
}
