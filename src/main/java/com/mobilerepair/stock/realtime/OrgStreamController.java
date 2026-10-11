package com.mobilerepair.stock.realtime;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/orgs")
public class OrgStreamController {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @GetMapping(path = "/{orgId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String orgId) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name("open").data(Map.of("orgId", orgId, "status", "connected")));
        } catch (IOException ignored) {
            emitter.complete();
        }
        var task = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("ping").data(Map.of("orgId", orgId)));
            } catch (Exception e) {
                emitter.complete();
            }
        }, 15, 15, TimeUnit.SECONDS);
        emitter.onCompletion(() -> task.cancel(true));
        emitter.onTimeout(() -> task.cancel(true));
        return emitter;
    }
}
