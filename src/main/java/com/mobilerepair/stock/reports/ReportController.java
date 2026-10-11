package com.mobilerepair.stock.reports;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Server-side report/export endpoints. Mirrors the client's
 * {@code exportInventoryToExcel}, {@code exportInventoryToCsv},
 * {@code downloadReport} and the sales-report filters, so all report
 * generation lives in Java.
 */
@RestController
@RequestMapping("/api/org/{orgId}/reports")
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping(value = "/inventory.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> inventoryCsv(@PathVariable String orgId,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) String category) {
        return attachment(reports.inventoryCsv(orgId, q, category).getBytes(StandardCharsets.UTF_8),
                "text/csv;charset=UTF-8", "inventory.csv");
    }

    @GetMapping(value = "/transactions.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> transactionsCsv(@PathVariable String orgId,
                                                  @RequestParam(required = false, name = "from") String from,
                                                  @RequestParam(required = false, name = "to") String to,
                                                  @RequestParam(required = false) String user,
                                                  @RequestParam(required = false) String type) {
        return attachment(reports.transactionsCsv(orgId, from, to, user, type).getBytes(StandardCharsets.UTF_8),
                "text/csv;charset=UTF-8", "transactions.csv");
    }

    @GetMapping(value = "/inventory.xlsx")
    public ResponseEntity<byte[]> inventoryXlsx(@PathVariable String orgId,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) String category) {
        return attachment(reports.inventoryXlsx(orgId, q, category), XLSX.toString(), "inventory.xlsx");
    }

    @GetMapping(value = "/report.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> reportJson(@PathVariable String orgId,
                                          @RequestParam(required = false, name = "from") String from,
                                          @RequestParam(required = false, name = "to") String to,
                                          @RequestParam(required = false) String user,
                                          @RequestParam(required = false) String type,
                                          @RequestHeader(value = "X-Org-User", required = false) String requestedBy) {
        return reports.reportJson(orgId, from, to, user, type, requestedBy);
    }

    private ResponseEntity<byte[]> attachment(byte[] body, String contentType, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
