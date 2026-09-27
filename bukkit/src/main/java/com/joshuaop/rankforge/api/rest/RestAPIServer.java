package com.joshuaop.rankforge.api.rest;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import com.joshuaop.rankforge.rank.RankModel;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Optional, lightweight embedded REST API server for external integrations.
 *
 * <p>Uses only Java standard library ({@code java.net.ServerSocket}) — no
 * additional dependencies required. Enable in config.yml:
 *
 * <pre>
 * rest-api:
 *   enabled: false
 *   port: 4567
 *   token: ""          # Bearer token auth; empty = no auth (development only!)
 * </pre>
 *
 * <h3>Endpoints (HTTP GET):</h3>
 * <ul>
 *   <li>{@code GET /api/status}             — plugin status + uptime.</li>
 *   <li>{@code GET /api/ranks}              — JSON array of all rank definitions.</li>
 *   <li>{@code GET /api/player/{uuid}}      — player's rank + XP data.</li>
 * </ul>
 *
 * <p>All responses are {@code application/json}. Authentication is via
 * {@code Authorization: Bearer <token>} header when a token is configured.
 *
 * <p><strong>Security note:</strong> Bind behind a reverse proxy or firewall
 * on production servers. Never expose this port directly to the internet.
 */
public class RestAPIServer {

    private static final int CLIENT_SOCKET_TIMEOUT_MS = 5000;
    private static final int MAX_WORKER_THREADS = 10;

    private final RankForge       plugin;
    private final Logger          log;
    private       Thread          serverThread;
    private       ServerSocket    serverSocket;
    private       ExecutorService clientExecutor;
    private volatile boolean      running = false;

    private record HttpResponse(int status, String body) {}

    public RestAPIServer(RankForge plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Start the REST API server (non-blocking — spawns a daemon thread). */
    public void start() {
        if (!plugin.getConfig().getBoolean("rest-api.enabled", false)) return;
        int port = plugin.getConfig().getInt("rest-api.port", 4567);

        String token = plugin.getConfig().getString("rest-api.token", "");
        if (token.isBlank()) {
            log.warning("[REST] WARNING: REST API is enabled without an authentication token! Endpoints are unauthenticated.");
        }

        try {
            serverSocket   = new ServerSocket(port);
            clientExecutor = Executors.newFixedThreadPool(MAX_WORKER_THREADS, r -> {
                Thread t = new Thread(r, "RankForge-REST-Worker");
                t.setDaemon(true);
                return t;
            });
            running = true;
        } catch (IOException e) {
            log.warning("[REST] Failed to bind on port " + port + ": " + e.getMessage());
            return;
        }

        serverThread = new Thread(() -> {
            log.info("[REST] API server listening on port " + port);
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(CLIENT_SOCKET_TIMEOUT_MS);
                    clientExecutor.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) log.warning("[REST] Accept error: " + e.getMessage());
                }
            }
        }, "RankForge-REST");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /** Stop the REST API server and worker threads. */
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); }
            catch (IOException ignored) {}
        }
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
            try {
                if (!clientExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    log.warning("[REST] Worker pool did not terminate cleanly within timeout.");
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── Request handling ──────────────────────────────────────────────────────

    private void handleClient(Socket client) {
        try (client;
             BufferedReader in  = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             OutputStream  out = client.getOutputStream()) {

            // Parse the first line of the HTTP request
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isBlank()) return;

            // Read headers (for auth)
            String authHeader = "";
            String line;
            while ((line = in.readLine()) != null && !line.isBlank()) {
                if (line.toLowerCase().startsWith("authorization:")) {
                    authHeader = line.substring(14).trim();
                }
            }

            // Auth check
            String token = plugin.getConfig().getString("rest-api.token", "");
            if (!token.isBlank()) {
                if (!authHeader.equals("Bearer " + token)) {
                    sendResponse(out, 401, "application/json", "{\"error\":\"Unauthorized\"}");
                    return;
                }
            }

            // Parse method + path
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String path   = parts[1].split("\\?")[0]; // strip query string

            if (!"GET".equalsIgnoreCase(method)) {
                sendResponse(out, 405, "application/json", "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            HttpResponse response = route(path);
            sendResponse(out, response.status(), "application/json", response.body());

        } catch (SocketTimeoutException e) {
            log.fine("[REST] Client read timed out.");
        } catch (Exception e) {
            log.fine("[REST] Client error: " + e.getMessage());
        }
    }

    private HttpResponse route(String path) {
        if (path.equals("/api/status")) return new HttpResponse(200, buildStatus());
        if (path.equals("/api/ranks"))  return new HttpResponse(200, buildRanks());
        if (path.startsWith("/api/player/")) {
            String uuidStr = path.substring("/api/player/".length());
            if (uuidStr.isBlank()) {
                return new HttpResponse(400, "{\"error\":\"Missing player UUID parameter\"}");
            }
            return buildPlayerResponse(uuidStr);
        }
        return new HttpResponse(404, "{\"error\":\"Not Found\"}");
    }

    // ── JSON builders ─────────────────────────────────────────────────────────

    private String buildStatus() {
        return "{\"plugin\":\"RankForge\","
                + "\"version\":\"" + plugin.getDescription().getVersion() + "\","
                + "\"ranks\":" + plugin.getRankManager().getRankCount() + ","
                + "\"players\":" + plugin.getRankManager().getCacheManager().size()
                + "}";
    }

    private String buildRanks() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (RankModel r : plugin.getRankManager().getModelList()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{")
              .append("\"id\":\"").append(escape(r.getId())).append("\",")
              .append("\"displayName\":\"").append(escape(r.getDisplayName())).append("\",")
              .append("\"nextRank\":\"").append(escape(r.getNextRankId())).append("\",")
              .append("\"slot\":").append(r.getSlot()).append(",")
              .append("\"requiredMoney\":").append(r.getRequiredMoney()).append(",")
              .append("\"requiredXpLevel\":").append(r.getRequiredXpLevel())
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private HttpResponse buildPlayerResponse(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            PlayerData data = plugin.getRankManager().getCacheManager().get(uuid);
            if (data == null) {
                return new HttpResponse(404, "{\"error\":\"Player not found in cache\"}");
            }
            String body = "{\"uuid\":\"" + escape(data.uuid().toString()) + "\","
                    + "\"name\":\"" + escape(data.playerName()) + "\","
                    + "\"rank\":\"" + escape(data.rankId()) + "\","
                    + "\"experience\":" + data.experience() + ","
                    + "\"money\":" + data.money()
                    + "}";
            return new HttpResponse(200, body);
        } catch (IllegalArgumentException e) {
            return new HttpResponse(400, "{\"error\":\"Invalid UUID format\"}");
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private void sendResponse(OutputStream out, int status, String contentType, String body)
            throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + status + " " + statusText(status) + "\r\n"
                + "Content-Type: " + contentType + "; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    private String statusText(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default  -> "Internal Server Error";
        };
    }

    /** Minimal JSON string escaping. */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public boolean isRunning() { return running; }
}
