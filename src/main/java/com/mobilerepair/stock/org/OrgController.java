package com.mobilerepair.stock.org;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST surface for the organisation business logic that used to live in
 * app.js. Each mutating call returns {@code { "state": <orgData>, "result":
 * <operation-specific> }} so the browser can re-render from the authoritative
 * server state without reimplementing any rules.
 */
@RestController
@RequestMapping("/api/org/{orgId}")
public class OrgController {

    private final OrgStateService service;

    public OrgController(OrgStateService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public Map<String, Object> state(@PathVariable String orgId) {
        return service.state(orgId);
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> summary(@PathVariable String orgId) {
        return service.reportSummary(orgId);
    }

    @PostMapping("/items")
    public Map<String, Object> addItem(@PathVariable String orgId,
                                       @RequestBody Map<String, Object> body,
                                       @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.addItem(orgId, body, user);
    }

    @DeleteMapping("/items/{id}")
    public Map<String, Object> deleteItem(@PathVariable String orgId,
                                          @PathVariable String id,
                                          @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.deleteItem(orgId, id, user);
    }

    @PostMapping("/items/{id}/stock-in")
    public Map<String, Object> stockIn(@PathVariable String orgId,
                                       @PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body,
                                       @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.stockIn(orgId, id, number(body, "amount"), reason(body), user);
    }

    @PostMapping("/items/{id}/stock-out")
    public Map<String, Object> stockOut(@PathVariable String orgId,
                                        @PathVariable String id,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.stockOut(orgId, id, number(body, "amount"), reason(body), user);
    }

    @PostMapping("/sales")
    public Map<String, Object> sale(@PathVariable String orgId,
                                    @RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.sale(orgId, str(body.get("id")), num(body.get("amount")),
                text(body.get("customer")), stringOrNull(body.get("reason")), user);
    }

    @PostMapping("/phone-trades")
    public Map<String, Object> phoneTrade(@PathVariable String orgId,
                                          @RequestBody Map<String, Object> body,
                                          @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.phoneTrade(orgId, body, user);
    }

    @PostMapping("/repairs")
    public Map<String, Object> createRepair(@PathVariable String orgId,
                                            @RequestBody Map<String, Object> body,
                                            @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.createRepair(orgId, body, user);
    }

    @PostMapping("/repairs/{ticketId}/complete")
    public Map<String, Object> completeRepair(@PathVariable String orgId,
                                              @PathVariable String ticketId,
                                              @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.completeRepair(orgId, ticketId, user);
    }

    @PutMapping("/preferences")
    public Map<String, Object> preferences(@PathVariable String orgId,
                                           @RequestBody Map<String, Object> body,
                                           @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.savePreferences(orgId, text(body.get("currency")), text(body.get("language")), user);
    }

    @PostMapping("/reset")
    public Map<String, Object> reset(@PathVariable String orgId,
                                     @RequestHeader(value = "X-Org-User", required = false) String user) {
        return service.reset(orgId, user);
    }

    private static double number(Map<String, Object> body, String key) {
        return body == null ? 0 : num(body.get(key));
    }

    private static String reason(Map<String, Object> body) {
        return body == null ? null : stringOrNull(body.get("reason"));
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double num(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s.isEmpty() ? null : s;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
