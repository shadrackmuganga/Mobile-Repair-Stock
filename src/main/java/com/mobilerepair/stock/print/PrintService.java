package com.mobilerepair.stock.print;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Sends raw bytes (typically ESC/POS commands) to a network / WiFi receipt
 * printer over a TCP socket. Most receipt printers listen on port 9100
 * (the "RAW"/JetDirect port), which is the default used by the UI.
 */
@Service
public class PrintService {

    private static final int DEFAULT_PORT = 9100;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    public int send(String host, Integer port, byte[] data) {
        String target = validateHost(host);
        int resolvedPort = validatePort(port);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target, resolvedPort), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(data == null ? new byte[0] : data);
            out.flush();
            return data == null ? 0 : data.length;
        } catch (IOException e) {
            throw new PrintException("Could not reach the printer at " + target + ":" + resolvedPort
                    + " — " + e.getMessage());
        }
    }

    /** Opens (and immediately closes) a TCP connection to confirm reachability. */
    public void probe(String host, Integer port) {
        String target = validateHost(host);
        int resolvedPort = validatePort(port);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target, resolvedPort), CONNECT_TIMEOUT_MS);
        } catch (IOException e) {
            throw new PrintException("Could not reach the printer at " + target + ":" + resolvedPort
                    + " — " + e.getMessage());
        }
    }

    private static String validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw new PrintException("Printer IP / host is required");
        }
        return host.trim();
    }

    private static int validatePort(Integer port) {
        int resolved = port == null ? DEFAULT_PORT : port;
        if (resolved < 1 || resolved > 65535) {
            throw new PrintException("Invalid printer port: " + resolved);
        }
        return resolved;
    }
}
