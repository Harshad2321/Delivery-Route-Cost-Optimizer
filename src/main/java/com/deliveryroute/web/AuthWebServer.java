package com.deliveryroute.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Executors;

import com.deliveryroute.database.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Lightweight HTTP server for serving web frontend and auth APIs.
 */
public final class AuthWebServer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UserRepository USER_REPOSITORY = new UserRepository();
    private static final Path WEB_ROOT = Paths.get("src", "main", "resources", "web").toAbsolutePath().normalize();

    private static HttpServer server;

    private AuthWebServer() {
    }

    public static synchronized void start(int port) {
        if (server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Register specific API endpoints BEFORE the static handler
            server.createContext("/api/orders", exchange -> {
                try {
                    ApiHandlers.handleOrderApi(exchange);
                } catch (Exception e) {
                    System.err.println("[ERROR] /api/orders: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        sendJson(exchange, 500, Map.of("success", false, "message", "Internal server error: " + e.getMessage()));
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            });
            
            server.createContext("/api/riders", exchange -> {
                try {
                    ApiHandlers.handleRiderApi(exchange);
                } catch (Exception e) {
                    System.err.println("[ERROR] /api/riders: " + e.getMessage());
                    e.printStackTrace();
                    try {
                        sendJson(exchange, 500, Map.of("success", false, "message", "Internal server error: " + e.getMessage()));
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            });
            
            server.createContext("/api/auth/login", AuthWebServer::handleLogin);
            server.createContext("/api/auth/register", AuthWebServer::handleRegister);
            
            // Static file handler last (catches everything else)
            server.createContext("/", AuthWebServer::handleStatic);
            
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("AuthWebServer running at http://localhost:" + port);
        } catch (IOException e) {
            System.err.println("Failed to start AuthWebServer: " + e.getMessage());
            server = null;
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public static void main(String[] args) {
        start(8081);
        System.out.println("Press Ctrl+C to stop server.");
    }

    private static void handleLogin(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            sendNoContent(exchange);
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
            return;
        }

        Map<String, String> payload = readJsonMap(exchange);
        String email = safeLower(payload.get("email"));
        String password = safe(payload.get("password"));
        String accountType = normalizeAccountType(payload.get("accountType"));

        if (email.isBlank() || password.isBlank()) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Email and password are required"));
            return;
        }

        boolean authenticated = USER_REPOSITORY.authenticate(email, password, accountType);
        if (!authenticated) {
            sendJson(exchange, 401, Map.of("success", false, "message", "Invalid credentials"));
            return;
        }

        sendJson(exchange, 200, Map.of(
                "success", true,
                "email", email,
                "accountType", accountType,
                "message", "Login successful"
        ));
    }

    private static void handleRegister(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            sendNoContent(exchange);
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
            return;
        }

        Map<String, String> payload = readJsonMap(exchange);
        String name = safe(payload.get("name"));
        String email = safeLower(payload.get("email"));
        String password = safe(payload.get("password"));
        String accountType = normalizeAccountType(payload.get("accountType"));

        if ("ADMIN".equals(accountType)) {
            sendJson(exchange, 403, Map.of("success", false, "message", "Admin signup is not allowed"));
            return;
        }

        if (name.length() < 2) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Name must be at least 2 characters"));
            return;
        }

        if (!isValidEmail(email)) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Valid email is required"));
            return;
        }

        if (password.length() < 4) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Password must be at least 4 characters"));
            return;
        }

        if (USER_REPOSITORY.emailExists(email)) {
            sendJson(exchange, 409, Map.of("success", false, "message", "Email already exists"));
            return;
        }

        boolean created = USER_REPOSITORY.registerUser(name, email, password, accountType);
        if (!created) {
            sendJson(exchange, 500, Map.of("success", false, "message", "Could not create account"));
            return;
        }

        sendJson(exchange, 201, Map.of(
                "success", true,
                "email", email,
                "accountType", accountType,
                "message", "Account created"
        ));
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            if (isPreflight(exchange)) {
                sendNoContent(exchange);
                return;
            }
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
            return;
        }

        URI uri = exchange.getRequestURI();
        String rawPath = uri.getPath();
        String normalizedPath = normalizeStaticPath(rawPath);
        Path resolved = WEB_ROOT.resolve(normalizedPath.substring(1)).normalize();

        if (!resolved.startsWith(WEB_ROOT) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
            sendText(exchange, 404, "Not Found", "text/plain; charset=UTF-8");
            return;
        }

        byte[] bytes = Files.readAllBytes(resolved);
        Headers headers = exchange.getResponseHeaders();
        setCors(headers);
        headers.set("Content-Type", contentTypeFor(resolved.getFileName().toString()));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> readJsonMap(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(body, new TypeReference<>() {
        });
    }

    private static String normalizeStaticPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
            return "/login/index.html";
        }

        String path = rawPath;
        if ("/login".equals(path)) {
            return "/login/index.html";
        }
        if ("/admin".equals(path) || "/admin/".equals(path)) {
            return "/login/admin/index.html";
        }
        if ("/login/admin".equals(path)) {
            return "/login/admin/index.html";
        }
        if (path.endsWith("/")) {
            return path + "index.html";
        }
        return path;
    }

    private static String contentTypeFor(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private static void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        setCors(headers);
        headers.set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        setCors(headers);
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static boolean isPreflight(HttpExchange exchange) {
        return "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static void sendNoContent(HttpExchange exchange) throws IOException {
        try (exchange) {
            Headers headers = exchange.getResponseHeaders();
            setCors(headers);
            exchange.sendResponseHeaders(204, -1);
        }
    }

    private static void setCors(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String normalizeAccountType(String value) {
        String normalized = safe(value).toUpperCase();
        return switch (normalized) {
            case "DELIVERY", "DELIVERY_PARTNER" -> "DELIVERY_PARTNER";
            case "ADMIN" -> "ADMIN";
            default -> "CUSTOMER";
        };
    }

    private static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeLower(String value) {
        return safe(value).toLowerCase();
    }
}
