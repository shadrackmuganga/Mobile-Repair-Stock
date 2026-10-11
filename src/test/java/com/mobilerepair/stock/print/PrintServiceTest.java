package com.mobilerepair.stock.print;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrintServiceTest {

    private final PrintService service = new PrintService();

    @Test
    void sendsBytesToPrinterSocket() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = CompletableFuture.supplyAsync(() -> {
                try (Socket socket = server.accept();
                     InputStream in = socket.getInputStream()) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[256];
                    int read;
                    while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
                    return buffer.toByteArray();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            byte[] payload = "HELLO-PRINTER".getBytes(StandardCharsets.US_ASCII);
            int written = service.send("127.0.0.1", server.getLocalPort(), payload);

            assertEquals(payload.length, written);
            assertEquals("HELLO-PRINTER", new String(received.get(), StandardCharsets.US_ASCII));
        }
    }

    @Test
    void probeConnectsWithoutWriting() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Boolean> accepted = CompletableFuture.supplyAsync(() -> {
                try (Socket ignored = server.accept()) {
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
            service.probe("127.0.0.1", server.getLocalPort());
            assertTrue(accepted.get());
        }
    }

    @Test
    void rejectsBlankHost() {
        PrintException error = assertThrows(PrintException.class, () -> service.send("  ", 9100, new byte[0]));
        assertTrue(error.getMessage().contains("required"));
    }

    @Test
    void reportsUnreachablePrinter() {
        PrintException error = assertThrows(PrintException.class,
                () -> service.send("127.0.0.1", 1, new byte[0]));
        assertTrue(error.getMessage().contains("Could not reach the printer"));
    }
}
