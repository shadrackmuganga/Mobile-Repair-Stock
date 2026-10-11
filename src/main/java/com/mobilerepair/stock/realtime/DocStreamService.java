package com.mobilerepair.stock.realtime;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DocStreamService {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String collection, String id) {
        String key = key(collection, id);
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(e -> remove(key, emitter));
        try {
            emitter.send(SseEmitter.event().name("open").data(Map.of("collection", collection, "id", id)));
        } catch (IOException e) {
            remove(key, emitter);
        }
        return emitter;
    }

    public void publish(String collection, String id, Object document) {
        String key = key(collection, id);
        List<SseEmitter> subscribers = emitters.get(key);
        if (subscribers == null) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("update").data(document));
            } catch (Exception e) {
                remove(key, emitter);
            }
        }
    }

    private void remove(String key, SseEmitter emitter) {
        List<SseEmitter> subscribers = emitters.get(key);
        if (subscribers != null) {
            subscribers.remove(emitter);
        }
    }

    private String key(String collection, String id) {
        return collection + "/" + id;
    }
}
