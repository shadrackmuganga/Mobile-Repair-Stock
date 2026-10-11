package com.mobilerepair.stock.print;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets the browser print to a network / WiFi receipt printer. The browser
 * converts the receipt/report into ESC/POS bytes, base64-encodes them, and the
 * backend streams them to the printer's TCP port (default 9100). Doing it
 * server-side avoids the CORS / mixed-content limits of raw sockets in the
 * browser.
 */
@RestController
@RequestMapping("/api/print")
public class PrintController {

    private final PrintService printService;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    @PostMapping("/network")
    public Map<String, Object> network(@RequestBody Map<String, Object> body) {
        String host = str(body.get("host"));
        Integer port = intOrNull(body.get("port"));
        String encoded = str(body.get("data"));

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded == null ? "" : encoded);
        } catch (IllegalArgumentException e) {
            throw new PrintException("Print payload was not valid base64");
        }

        int written = printService.send(host, port, bytes);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("bytes", written);
        return response;
    }

    @PostMapping("/network/test")
    public Map<String, Object> test(@RequestBody Map<String, Object> body) {
        printService.probe(str(body.get("host")), intOrNull(body.get("port")));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        return response;
    }

    @ExceptionHandler(PrintException.class)
    public ResponseEntity<Map<String, Object>> handlePrint(PrintException error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", error.getMessage());
        body.put("error", "print-failed");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return (int) Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
