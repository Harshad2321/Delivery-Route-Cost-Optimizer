package com.deliveryroute.web;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.deliveryroute.database.OrderRepository;
import com.deliveryroute.database.RiderRepository;
import com.deliveryroute.model.Rider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

/**
 * REST API handlers for Order and Rider CRUD operations.
 */

public final class ApiHandlers {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static OrderRepository ORDER_REPO;
    private static RiderRepository RIDER_REPO;

    private ApiHandlers() {
    }

    /**
     * Lazily initialize OrderRepository to avoid class loading failures
     */
    private static OrderRepository getOrderRepo() {
        if (ORDER_REPO == null) {
            synchronized (ApiHandlers.class) {
                if (ORDER_REPO == null) {
                    try {
                        ORDER_REPO = new OrderRepository();
                        System.out.println("[DEBUG] OrderRepository initialized");
                    } catch (Exception e) {
                        System.err.println("[ERROR] Failed to initialize OrderRepository: " + e.getMessage());
                        e.printStackTrace();
                        throw new RuntimeException("Failed to initialize OrderRepository", e);
                    }
                }
            }
        }
        return ORDER_REPO;
    }

    /**
     * Lazily initialize RiderRepository to avoid class loading failures
     */
    private static RiderRepository getRiderRepo() {
        if (RIDER_REPO == null) {
            synchronized (ApiHandlers.class) {
                if (RIDER_REPO == null) {
                    try {
                        RIDER_REPO = new RiderRepository();
                        System.out.println("[DEBUG] RiderRepository initialized");
                    } catch (Exception e) {
                        System.err.println("[ERROR] Failed to initialize RiderRepository: " + e.getMessage());
                        e.printStackTrace();
                        throw new RuntimeException("Failed to initialize RiderRepository", e);
                    }
                }
            }
        }
        return RIDER_REPO;
    }
    /**
     * Handle Order API requests (GET, POST, PUT, DELETE)
     */
    public static void handleOrderApi(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            sendNoContent(exchange);
            return;
        }

        String method = exchange.getRequestMethod();
        String query = exchange.getRequestURI().getQuery();
        System.out.println("[DEBUG] /api/orders method=" + method + ", query=" + query);

        if ("GET".equalsIgnoreCase(method)) {
            handleOrderGet(exchange, query);
        } else if ("POST".equalsIgnoreCase(method)) {
            handleOrderPost(exchange);
        } else if ("PUT".equalsIgnoreCase(method)) {
            handleOrderPut(exchange);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            handleOrderDelete(exchange, query);
        } else {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
        }
    }

    private static void handleOrderGet(HttpExchange exchange, String query) throws IOException {
        // GET /api/orders?id=123
        if (query != null && query.startsWith("id=")) {
            try {
                long orderId = Long.parseLong(query.substring(3));
                    OrderRepository.OrderRecord order = getOrderRepo().getOrderById(orderId);
                if (order != null) {
                    sendJson(exchange, 200, Map.of(
                            "success", true,
                            "order", orderToMap(order)
                    ));
                } else {
                    sendJson(exchange, 404, Map.of("success", false, "message", "Order not found"));
                }
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Invalid order ID"));
            }
        } else {
            // GET /api/orders - get all orders
                List<OrderRepository.OrderRecord> orders = getOrderRepo().getAllOrders();
            sendJson(exchange, 200, Map.of(
                    "success", true,
                    "orders", orders.stream().map(ApiHandlers::orderToMap).toList(),
                    "count", orders.size()
            ));
        }
    }

    private static void handleOrderPost(HttpExchange exchange) throws IOException {
        // POST /api/orders - create new order
        Map<String, Object> payload = readJsonMap(exchange);
        
        try {
            String customerEmail = safe(payload.get("customerEmail"));
            String pickupCity = safe(payload.get("pickupCity"));
            String dropCity = safe(payload.get("dropCity"));
            String vehicleType = safe(payload.get("vehicleType"));
            double weightKg = toDouble(payload.get("weightKg"), 0.0);
            String pickupSlot = safe(payload.get("pickupSlot"));
            double totalCost = toDouble(payload.get("totalCost"), Double.NaN);

            if (customerEmail.isBlank() || pickupCity.isBlank() || dropCity.isBlank() || vehicleType.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Missing required fields"));
                return;
            }

            long orderId;
            if (Double.isNaN(totalCost)) {
                orderId = getOrderRepo().createOrderViaApi(customerEmail, pickupCity, dropCity, vehicleType, weightKg, pickupSlot);
            } else {
                orderId = getOrderRepo().createOrderViaApi(customerEmail, pickupCity, dropCity, vehicleType, weightKg, pickupSlot, totalCost);
            }
            OrderRepository.OrderRecord order = getOrderRepo().getOrderById(orderId);
            sendJson(exchange, 201, Map.of(
                    "success", true,
                    "message", "Order created successfully",
                    "orderId", orderId,
                    "order", orderToMap(order)
            ));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("success", false, "message", e.getMessage()));
        }
    }

    private static void handleOrderPut(HttpExchange exchange) throws IOException {
        // PUT /api/orders - update order
        Map<String, Object> payload = readJsonMap(exchange);

        try {
            long orderId = toLong(payload.get("orderId"), -1);
            if (orderId <= 0) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Order ID required"));
                return;
            }

            String action = safe(payload.get("action"));

            if ("updateStatus".equals(action)) {
                String status = safe(payload.get("status"));
                boolean success = getOrderRepo().updateOrderStatusByAdmin(orderId, status);
                sendJson(exchange, success ? 200 : 400, Map.of(
                        "success", success,
                        "message", success ? "Order status updated" : "Failed to update order status"
                ));
            } else if ("acceptOrder".equals(action)) {
                String deliveryUsername = safe(payload.get("deliveryUsername"));
                OrderRepository.OrderRecord accepted = getOrderRepo().acceptOrderForDelivery(orderId, deliveryUsername);
                if (accepted == null) {
                    sendJson(exchange, 400, Map.of("success", false, "message", "Order could not be accepted"));
                    return;
                }
                sendJson(exchange, 200, Map.of(
                        "success", true,
                        "message", "Order accepted",
                        "order", orderToMap(accepted)
                ));
            } else if ("releaseOrder".equals(action)) {
                String deliveryUsername = safe(payload.get("deliveryUsername"));
                boolean success = getOrderRepo().releaseAcceptedOrder(orderId, deliveryUsername);
                sendJson(exchange, success ? 200 : 400, Map.of(
                        "success", success,
                        "message", success ? "Order released" : "Failed to release order"
                ));
            } else if ("updateDetails".equals(action)) {
                String pickupCity = safe(payload.get("pickupCity"));
                String dropCity = safe(payload.get("dropCity"));
                String vehicleType = safe(payload.get("vehicleType"));
                double weightKg = toDouble(payload.get("weightKg"), 0.0);
                String status = safe(payload.get("status"));
                String pickupSlot = safe(payload.get("pickupSlot"));

                    boolean success = getOrderRepo().updateOrderViaApi(orderId, pickupCity, dropCity, vehicleType, weightKg, status, pickupSlot);
                sendJson(exchange, success ? 200 : 400, Map.of(
                        "success", success,
                        "message", success ? "Order updated" : "Failed to update order"
                ));
            } else if ("markPicked".equals(action)) {
                boolean success = getOrderRepo().markOrderPicked(orderId);
                sendJson(exchange, success ? 200 : 400, Map.of(
                        "success", success,
                        "message", success ? "Order marked as picked" : "Failed to mark as picked"
                ));
            } else if ("markInTransit".equals(action)) {
                boolean success = getOrderRepo().markOrderInTransit(orderId);
                sendJson(exchange, success ? 200 : 400, Map.of(
                        "success", success,
                        "message", success ? "Order marked as in transit" : "Failed to mark as in transit"
                ));
            } else {
                sendJson(exchange, 400, Map.of("success", false, "message", "Unknown action"));
            }
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("success", false, "message", e.getMessage()));
        }
    }

    private static void handleOrderDelete(HttpExchange exchange, String query) throws IOException {
        // DELETE /api/orders?id=123
        try {
            if (query == null || !query.startsWith("id=")) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Order ID required"));
                return;
            }

            long orderId = Long.parseLong(query.substring(3));
                boolean success = getOrderRepo().deleteOrderByAdmin(orderId);
            sendJson(exchange, success ? 200 : 400, Map.of(
                    "success", success,
                    "message", success ? "Order deleted" : "Failed to delete order"
            ));
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Invalid order ID"));
        }
    }

    /**
     * Handle Rider API requests (GET, POST, PUT, DELETE)
     */
    public static void handleRiderApi(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            sendNoContent(exchange);
            return;
        }

        String method = exchange.getRequestMethod();
        String query = exchange.getRequestURI().getQuery();

        if ("GET".equalsIgnoreCase(method)) {
            handleRiderGet(exchange, query);
        } else if ("POST".equalsIgnoreCase(method)) {
            handleRiderPost(exchange);
        } else if ("PUT".equalsIgnoreCase(method)) {
            handleRiderPut(exchange);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            handleRiderDelete(exchange, query);
        } else {
            sendJson(exchange, 405, Map.of("success", false, "message", "Method not allowed"));
        }
    }

    private static void handleRiderGet(HttpExchange exchange, String query) throws IOException {
        if (query != null && query.startsWith("id=")) {
            try {
                long riderId = Long.parseLong(query.substring(3));
                Rider rider = getRiderRepo().getRiderById(riderId);
                if (rider != null) {
                    sendJson(exchange, 200, Map.of(
                            "success", true,
                            "rider", riderToMap(rider)
                    ));
                } else {
                    sendJson(exchange, 404, Map.of("success", false, "message", "Rider not found"));
                }
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Invalid rider ID"));
            }
        } else {
            // GET /api/riders - get all riders
                List<Rider> riders = getRiderRepo().getAllRiders();
            sendJson(exchange, 200, Map.of(
                    "success", true,
                    "riders", riders.stream().map(ApiHandlers::riderToMap).toList(),
                    "count", riders.size()
            ));
        }
    }

    private static void handleRiderPost(HttpExchange exchange) throws IOException {
        // POST /api/riders - create new rider
        Map<String, Object> payload = readJsonMap(exchange);

        try {
            String name = safe(payload.get("name"));
            String phoneNumber = safe(payload.get("phoneNumber"));
            String vehicleType = safe(payload.get("vehicleType"));
            String currentCity = safe(payload.get("currentCity"));

            if (name.isBlank() || phoneNumber.isBlank() || vehicleType.isBlank() || currentCity.isBlank()) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Missing required fields"));
                return;
            }


            long riderId = getRiderRepo().createRider(name, phoneNumber, vehicleType, currentCity);
                Rider rider = getRiderRepo().getRiderById(riderId);
            sendJson(exchange, 201, Map.of(
                    "success", true,
                    "message", "Rider created successfully",
                    "riderId", riderId,
                    "rider", riderToMap(rider)
            ));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("success", false, "message", e.getMessage()));
        }
    }

    private static void handleRiderPut(HttpExchange exchange) throws IOException {
        // PUT /api/riders - update rider
        Map<String, Object> payload = readJsonMap(exchange);

        try {
            long riderId = toLong(payload.get("riderId"), -1);
            if (riderId <= 0) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Rider ID required"));
                return;
            }

            String name = safe(payload.get("name"));
            String phoneNumber = safe(payload.get("phoneNumber"));
            String vehicleType = safe(payload.get("vehicleType"));
            String availability = safe(payload.get("availability"));
            String currentCity = safe(payload.get("currentCity"));
            double currentLat = toDouble(payload.get("currentLat"), 0.0);
            double currentLng = toDouble(payload.get("currentLng"), 0.0);

            boolean success = getRiderRepo().updateRider(riderId, name, phoneNumber, vehicleType, availability, currentCity, currentLat, currentLng);
            sendJson(exchange, success ? 200 : 400, Map.of(
                    "success", success,
                    "message", success ? "Rider updated" : "Failed to update rider"
            ));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("success", false, "message", e.getMessage()));
        }
    }

    private static void handleRiderDelete(HttpExchange exchange, String query) throws IOException {
        // DELETE /api/riders?id=123
        try {
            if (query == null || !query.startsWith("id=")) {
                sendJson(exchange, 400, Map.of("success", false, "message", "Rider ID required"));
                return;
            }

            long riderId = Long.parseLong(query.substring(3));
                boolean success = getRiderRepo().deleteRider(riderId);
            sendJson(exchange, success ? 200 : 400, Map.of(
                    "success", success,
                    "message", success ? "Rider deleted" : "Failed to delete rider"
            ));
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, Map.of("success", false, "message", "Invalid rider ID"));
        }
    }

    // Helper methods
    private static Map<String, Object> orderToMap(OrderRepository.OrderRecord order) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", order.getId());
        map.put("customerEmail", order.getCustomerUsername());
        map.put("sourceCity", order.getSourceCity());
        map.put("destinationCity", order.getDestinationCity());
        map.put("vehicleType", order.getVehicleType());
        map.put("totalCost", order.getTotalCost());
        map.put("status", order.getStatus());
        map.put("assignedRider", order.getAssignedDeliveryUsername());
        map.put("weightKg", order.getWeightKg());
        map.put("pickupSlot", order.getPickupSlot());
        map.put("placedAt", order.getPlacedAt());
        map.put("estimatedMinutes", order.getEstimatedMinutes());
        return map;
    }

    private static Map<String, Object> riderToMap(Rider rider) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", rider.getId());
        map.put("name", rider.getName());
        map.put("phoneNumber", rider.getPhoneNumber());
        map.put("vehicleType", rider.getVehicleType());
        map.put("availability", rider.getAvailability());
        map.put("currentCity", rider.getCurrentCity());
        map.put("currentLat", rider.getCurrentLat());
        map.put("currentLng", rider.getCurrentLng());
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJsonMap(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(body, Map.class);
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
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long toLong(Object value, long defaultValue) {
        try {
            return Long.parseLong(safe(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double toDouble(Object value, double defaultValue) {
        try {
            return Double.parseDouble(safe(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
