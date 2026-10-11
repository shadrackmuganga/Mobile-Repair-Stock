package com.mobilerepair.stock.health;

import com.mobilerepair.stock.auth.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final SessionService sessionService;

    public HealthController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ok");
        status.put("firebaseAuth", sessionService.isAvailable());
        return status;
    }
}
