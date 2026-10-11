package com.mobilerepair.stock.docs;

import com.mobilerepair.stock.realtime.DocStreamService;
import com.mobilerepair.stock.store.DocStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/db")
public class DocController {

    private final DocStore store;
    private final DocStreamService streams;

    public DocController(DocStore store, DocStreamService streams) {
        this.store = store;
        this.streams = streams;
    }

    @GetMapping("/{collection}")
    public List<Map<String, Object>> list(@PathVariable String collection) {
        return store.list(collection);
    }

    @GetMapping("/{collection}/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String collection,
                                                   @PathVariable String id) {
        return store.get(collection, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{collection}/{id}")
    public ResponseEntity<Void> put(@PathVariable String collection,
                                    @PathVariable String id,
                                    @RequestBody Map<String, Object> document) {
        store.put(collection, id, document);
        streams.publish(collection, id, document);
        return ResponseEntity.ok().build();
    }

    @GetMapping(path = "/{collection}/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String collection, @PathVariable String id) {
        return streams.subscribe(collection, id);
    }
}
