package com.mobilerepair.stock.store;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestoreDocStore implements DocStore {

    private final Firestore firestore;

    public FirestoreDocStore(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public List<Map<String, Object>> list(String collection) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (QueryDocumentSnapshot doc : firestore.collection(collection).get().get().getDocuments()) {
                result.add(withId(doc.getId(), doc.getData()));
            }
            return result;
        } catch (Exception e) {
            throw new DocStoreException("Unable to list " + collection, e);
        }
    }

    @Override
    public Optional<Map<String, Object>> get(String collection, String id) {
        try {
            DocumentSnapshot doc = firestore.collection(collection).document(id).get().get();
            return doc.exists() ? Optional.of(withId(doc.getId(), doc.getData())) : Optional.empty();
        } catch (Exception e) {
            throw new DocStoreException("Unable to read " + collection + "/" + id, e);
        }
    }

    @Override
    public void put(String collection, String id, Map<String, Object> document) {
        try {
            firestore.collection(collection).document(id).set(document).get();
        } catch (Exception e) {
            throw new DocStoreException("Unable to write " + collection + "/" + id, e);
        }
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
