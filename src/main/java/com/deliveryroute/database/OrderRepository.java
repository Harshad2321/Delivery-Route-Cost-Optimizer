package com.deliveryroute.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.deliveryroute.model.Route;

/**
 * Repository for customer orders and delivery assignment workflow.
 */
public class OrderRepository {
    private final DatabaseConnection dbConnection;

    public OrderRepository() {
        this.dbConnection = DatabaseConnection.getInstance();
        ensureOrderTable();
    }

    private void ensureOrderTable() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        String sql = """
                CREATE TABLE IF NOT EXISTS delivery_orders (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    customer_email VARCHAR(191) NOT NULL,
                    customer_username VARCHAR(100),
                    source_city VARCHAR(100) NOT NULL,
                    destination_city VARCHAR(100) NOT NULL,
                    vehicle_type VARCHAR(50) NOT NULL,
                    cost_strategy VARCHAR(50) NOT NULL,
                    total_cost DECIMAL(10,2) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    assigned_delivery_username VARCHAR(100) NULL,
                    assigned_rider_id BIGINT NULL,
                    weight_kg DECIMAL(10,2) DEFAULT 0,
                    pickup_slot VARCHAR(30),
                    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    accepted_at TIMESTAMP NULL,
                    picked_at TIMESTAMP NULL,
                    in_transit_at TIMESTAMP NULL,
                    delivered_at TIMESTAMP NULL,
                    estimated_minutes INT DEFAULT NULL,
                    actual_delivery DATETIME DEFAULT NULL,
                    CONSTRAINT chk_status CHECK (status IN ('CREATED', 'PLACED', 'ACCEPTED', 'PICKED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
                    INDEX idx_status (status),
                    INDEX idx_customer_email (customer_email),
                    INDEX idx_assigned_rider (assigned_rider_id),
                    INDEX idx_placed_at (placed_at DESC)
                )
                """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            // Keep existing DBs compatible while adding new columns used by v2 features.
            ensureColumn(stmt, "ALTER TABLE delivery_orders ADD COLUMN assigned_rider_id BIGINT NULL");
            ensureColumn(stmt, "ALTER TABLE delivery_orders ADD COLUMN weight_kg DECIMAL(10,2) DEFAULT 0");
            ensureColumn(stmt, "ALTER TABLE delivery_orders ADD COLUMN pickup_slot VARCHAR(30)");
            ensureColumn(stmt, "ALTER TABLE delivery_orders ADD COLUMN picked_at TIMESTAMP NULL");
            ensureColumn(stmt, "ALTER TABLE delivery_orders ADD COLUMN in_transit_at TIMESTAMP NULL");
            ensureColumn(stmt, "ALTER TABLE delivery_orders ADD COLUMN customer_email VARCHAR(191)");
        } catch (SQLException e) {
            System.err.println("Error ensuring order table: " + e.getMessage());
        }
    }

    private void ensureColumn(Statement stmt, String alterSql) {
        try {
            stmt.execute(alterSql);
        } catch (SQLException ignored) {
            // Ignore duplicate-column errors for idempotent startup.
        }
    }

    public void placeOrder(Route route, String sourceCity, String destinationCity, String customerUsername) {
        if (!dbConnection.isAvailable()) {
            throw new RuntimeException("Database unavailable. Cannot place order.");
        }

        String sql = """
                INSERT INTO delivery_orders (
                    customer_username, source_city, destination_city, vehicle_type,
                    cost_strategy, total_cost, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'PLACED')
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customerUsername);
            stmt.setString(2, sourceCity);
            stmt.setString(3, destinationCity);
            stmt.setString(4, route.getVehicleType());
            stmt.setString(5, route.getCostStrategy());
            stmt.setDouble(6, route.getTotalCost());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to place order: " + e.getMessage(), e);
        }
    }

    public List<OrderRecord> getAllOrders() {
        return getOrdersByQuery("SELECT * FROM delivery_orders ORDER BY placed_at DESC", null);
    }

    public List<OrderRecord> getOrdersByCustomer(String customerUsername) {
        return getOrdersByQuery("SELECT * FROM delivery_orders WHERE customer_username = ? ORDER BY placed_at DESC", customerUsername);
    }

    public List<OrderRecord> getOrdersForDeliveryView(String deliveryUsername) {
        String sql = """
                SELECT *
                FROM delivery_orders
                WHERE status = 'PLACED'
                   OR (status = 'ACCEPTED' AND assigned_delivery_username = ?)
                ORDER BY placed_at ASC
                """;
        return getOrdersByQuery(sql, deliveryUsername);
    }

    public OrderRecord acceptNextOrder(String deliveryUsername) {
        if (!dbConnection.isAvailable()) {
            throw new RuntimeException("Database unavailable. Cannot accept order.");
        }

        String pickSql = "SELECT id FROM delivery_orders WHERE status = 'PLACED' ORDER BY placed_at ASC LIMIT 1";

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Long orderId = null;
                try (PreparedStatement pickStmt = conn.prepareStatement(pickSql);
                     ResultSet rs = pickStmt.executeQuery()) {
                    if (rs.next()) {
                        orderId = rs.getLong("id");
                    }
                }

                if (orderId == null) {
                    conn.rollback();
                    return null;
                }

                String updateSql = """
                        UPDATE delivery_orders
                        SET status = 'ACCEPTED',
                            assigned_delivery_username = ?,
                            accepted_at = ?
                        WHERE id = ? AND status = 'PLACED'
                        """;

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, deliveryUsername);
                    updateStmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                    updateStmt.setLong(3, orderId);

                    int updated = updateStmt.executeUpdate();
                    if (updated == 0) {
                        conn.rollback();
                        return null;
                    }
                }

                conn.commit();
                return getOrderById(orderId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to accept order: " + e.getMessage(), e);
        }
    }

    public boolean markOrderDelivered(long orderId, String deliveryUsername) {
        if (!dbConnection.isAvailable()) {
            throw new RuntimeException("Database unavailable. Cannot complete order.");
        }

        String sql = """
                UPDATE delivery_orders
                                SET status = 'DELIVERED', delivered_at = ?, actual_delivery = ?
                WHERE id = ?
                  AND status = 'ACCEPTED'
                  AND assigned_delivery_username = ?
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            stmt.setTimestamp(1, now);
            stmt.setTimestamp(2, now);
            stmt.setLong(3, orderId);
            stmt.setString(4, deliveryUsername);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to complete order: " + e.getMessage(), e);
        }
    }

    public boolean completeOrder(long orderId, String deliveryUsername) {
        return markOrderDelivered(orderId, deliveryUsername);
    }

    public boolean assignOrder(long orderId, String deliveryUsername) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                UPDATE delivery_orders
                SET assigned_delivery_username = ?,
                    status = 'ACCEPTED',
                    accepted_at = ?
                WHERE id = ? AND status = 'PLACED'
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, deliveryUsername);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(3, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error assigning order: " + e.getMessage());
            return false;
        }
    }

    public boolean updateOrderStatusByAdmin(long orderId, String status) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("CREATED", "PLACED", "ACCEPTED", "PICKED", "IN_TRANSIT", "DELIVERED", "CANCELLED").contains(normalized)) {
            return false;
        }

        String sql = """
                UPDATE delivery_orders
                SET status = ?
                WHERE id = ?
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalized);
            stmt.setLong(2, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating order status by admin: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create an order via admin API with full details
     */
    public long createOrderViaApi(String customerEmail, String pickupCity, String dropCity, 
                                   String vehicleType, double weightKg, String pickupSlot) {
        if (!dbConnection.isAvailable()) {
            throw new RuntimeException("Database unavailable. Cannot create order.");
        }

        // Default cost calculation for now
        double totalCost = estimateCost(vehicleType, 50.0); // default 50km

        String sql = """
                INSERT INTO delivery_orders (
                    customer_email, source_city, destination_city, vehicle_type,
                    cost_strategy, total_cost, status, weight_kg, pickup_slot
                ) VALUES (?, ?, ?, ?, 'STANDARD', ?, 'CREATED', ?, ?)
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, customerEmail);
            stmt.setString(2, pickupCity);
            stmt.setString(3, dropCity);
            stmt.setString(4, vehicleType);
            stmt.setDouble(5, totalCost);
            stmt.setDouble(6, weightKg);
            stmt.setString(7, pickupSlot);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create order via API: " + e.getMessage(), e);
        }

        throw new RuntimeException("Failed to generate order ID");
    }

    /**
     * Update order via admin API
     */
    public boolean updateOrderViaApi(long orderId, String pickupCity, String dropCity, 
                                      String vehicleType, double weightKg, String status, String pickupSlot) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                UPDATE delivery_orders
                SET source_city = ?, destination_city = ?, vehicle_type = ?, weight_kg = ?, status = ?, pickup_slot = ?
                WHERE id = ?
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pickupCity);
            stmt.setString(2, dropCity);
            stmt.setString(3, vehicleType);
            stmt.setDouble(4, weightKg);
            stmt.setString(5, status);
            stmt.setString(6, pickupSlot);
            stmt.setLong(7, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating order via API: " + e.getMessage());
            return false;
        }
    }

    /**
     * Mark order as picked up
     */
    public boolean markOrderPicked(long orderId) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                UPDATE delivery_orders
                SET status = 'PICKED', picked_at = ?
                WHERE id = ? AND status IN ('ACCEPTED', 'PICKED')
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(2, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error marking order as picked: " + e.getMessage());
            return false;
        }
    }

    /**
     * Mark order as in transit
     */
    public boolean markOrderInTransit(long orderId) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                UPDATE delivery_orders
                SET status = 'IN_TRANSIT', in_transit_at = ?
                WHERE id = ? AND status IN ('PICKED', 'IN_TRANSIT')
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setLong(2, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error marking order as in transit: " + e.getMessage());
            return false;
        }
    }

    /**
     * Simple cost estimator
     */
    private double estimateCost(String vehicleType, double distanceKm) {
        double pricePerKm = switch (vehicleType) {
            case "Bike" -> 2.0;
            case "Van" -> 3.5;
            case "Truck" -> 5.0;
            default -> 2.5;
        };
        return distanceKm * pricePerKm;
    }

    public boolean deleteOrderByAdmin(long orderId) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "DELETE FROM delivery_orders WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting order by admin: " + e.getMessage());
            return false;
        }
    }

    public boolean updateEstimatedMinutes(long orderId, int estimatedMinutes) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "UPDATE delivery_orders SET estimated_minutes = ? WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, estimatedMinutes);
            stmt.setLong(2, orderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating estimated minutes: " + e.getMessage());
            return false;
        }
    }

    public OrderRecord getOrderById(long id) {
        return fetchOrderById(id);
    }

    public long getTodaysOrderCount() {
        return queryLong("SELECT COUNT(*) FROM delivery_orders WHERE DATE(placed_at) = CURDATE()");
    }

    public double getTodaysRevenue() {
        return queryDouble("SELECT COALESCE(SUM(total_cost), 0) FROM delivery_orders WHERE DATE(placed_at) = CURDATE()");
    }

    public long getActiveDeliveriesCount() {
        return queryLong("SELECT COUNT(*) FROM delivery_orders WHERE status = 'ACCEPTED'");
    }

    public Map<String, Long> getStatusBreakdown() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("PLACED", 0L);
        result.put("ACCEPTED", 0L);
        result.put("DELIVERED", 0L);

        if (!dbConnection.isAvailable()) {
            return result;
        }

        String sql = "SELECT status, COUNT(*) AS count_value FROM delivery_orders GROUP BY status";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String status = rs.getString("status");
                long count = rs.getLong("count_value");
                result.put(status, count);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching status breakdown: " + e.getMessage());
        }
        return result;
    }

    public List<DailyRevenuePoint> getRevenueLast7Days() {
        List<DailyRevenuePoint> points = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return points;
        }

        String sql = """
                SELECT DATE(placed_at) AS order_day, COALESCE(SUM(total_cost), 0) AS total_revenue
                FROM delivery_orders
                WHERE placed_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                GROUP BY DATE(placed_at)
                ORDER BY order_day ASC
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                points.add(new DailyRevenuePoint(
                        rs.getString("order_day"),
                        rs.getDouble("total_revenue")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching revenue points: " + e.getMessage());
        }

        return points;
    }

    private long queryLong(String sql) {
        if (!dbConnection.isAvailable()) {
            return 0L;
        }

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            System.err.println("Error executing count query: " + e.getMessage());
            return 0L;
        }
    }

    private double queryDouble(String sql) {
        if (!dbConnection.isAvailable()) {
            return 0.0;
        }

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            System.err.println("Error executing numeric query: " + e.getMessage());
            return 0.0;
        }
    }

    public OrderRecord getActiveAcceptedOrder(String deliveryUsername) {
        String sql = """
                SELECT *
                FROM delivery_orders
                WHERE status = 'ACCEPTED'
                  AND assigned_delivery_username = ?
                ORDER BY accepted_at DESC
                LIMIT 1
                """;

        List<OrderRecord> records = getOrdersByQuery(sql, deliveryUsername);
        return records.isEmpty() ? null : records.get(0);
    }

    public boolean hasActiveAcceptedOrder(String deliveryUsername) {
        return getActiveAcceptedOrder(deliveryUsername) != null;
    }

    public List<OrderRecord> getDeliveredUnratedOrdersForCustomer(String customerUsername) {
        String sql = """
                SELECT o.*
                FROM delivery_orders o
                LEFT JOIN delivery_ratings r ON r.order_id = o.id
                WHERE o.customer_username = ?
                  AND o.status = 'DELIVERED'
                  AND r.id IS NULL
                ORDER BY o.delivered_at DESC
                """;
        return getOrdersByQuery(sql, customerUsername);
    }

    private List<OrderRecord> getOrdersByQuery(String sql, String singleParam) {
        List<OrderRecord> orders = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return orders;
        }

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (singleParam != null) {
                stmt.setString(1, singleParam);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching orders: " + e.getMessage());
        }

        return orders;
    }

    private OrderRecord fetchOrderById(long id) {
        String sql = "SELECT * FROM delivery_orders WHERE id = ?";
        if (!dbConnection.isAvailable()) {
            return null;
        }

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRecord(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching order by id: " + e.getMessage());
        }

        return null;
    }

    private OrderRecord mapRecord(ResultSet rs) throws SQLException {
        return new OrderRecord(
                rs.getLong("id"),
                rs.getString("customer_username"),
                rs.getString("source_city"),
                rs.getString("destination_city"),
                rs.getString("vehicle_type"),
                rs.getString("cost_strategy"),
                rs.getDouble("total_cost"),
                rs.getString("status"),
                rs.getString("assigned_delivery_username"),
                rs.getString("placed_at"),
                rs.getString("accepted_at"),
                rs.getString("delivered_at"),
                rs.getInt("estimated_minutes"),
                rs.getString("actual_delivery")
        );
    }

    public record DailyRevenuePoint(String date, double revenue) {
    }

    public static class OrderRecord {
        private final long id;
        private final String customerUsername;
        private final String sourceCity;
        private final String destinationCity;
        private final String vehicleType;
        private final String costStrategy;
        private final double totalCost;
        private final String status;
        private final String assignedDeliveryUsername;
        private final String placedAt;
        private final String acceptedAt;
        private final String deliveredAt;
        private final Integer estimatedMinutes;
        private final String actualDelivery;

        public OrderRecord(long id,
                           String customerUsername,
                           String sourceCity,
                           String destinationCity,
                           String vehicleType,
                           String costStrategy,
                           double totalCost,
                           String status,
                           String assignedDeliveryUsername,
                           String placedAt,
                           String acceptedAt,
                           String deliveredAt,
                           Integer estimatedMinutes,
                           String actualDelivery) {
            this.id = id;
            this.customerUsername = customerUsername;
            this.sourceCity = sourceCity;
            this.destinationCity = destinationCity;
            this.vehicleType = vehicleType;
            this.costStrategy = costStrategy;
            this.totalCost = totalCost;
            this.status = status;
            this.assignedDeliveryUsername = assignedDeliveryUsername;
            this.placedAt = placedAt;
            this.acceptedAt = acceptedAt;
            this.deliveredAt = deliveredAt;
            this.estimatedMinutes = estimatedMinutes;
            this.actualDelivery = actualDelivery;
        }

        public long getId() {
            return id;
        }

        public String getCustomerUsername() {
            return customerUsername;
        }

        public String getSourceCity() {
            return sourceCity;
        }

        public String getDestinationCity() {
            return destinationCity;
        }

        public String getVehicleType() {
            return vehicleType;
        }

        public String getCostStrategy() {
            return costStrategy;
        }

        public double getTotalCost() {
            return totalCost;
        }

        public String getStatus() {
            return status;
        }

        public String getAssignedDeliveryUsername() {
            return assignedDeliveryUsername;
        }

        public String getPlacedAt() {
            return placedAt;
        }

        public String getAcceptedAt() {
            return acceptedAt;
        }

        public String getDeliveredAt() {
            return deliveredAt;
        }

        public Integer getEstimatedMinutes() {
            return estimatedMinutes;
        }

        public String getActualDelivery() {
            return actualDelivery;
        }

        @Override
        public String toString() {
            String assignee = assignedDeliveryUsername == null ? "-" : assignedDeliveryUsername;
            return "#" + id + " | " + sourceCity + " -> " + destinationCity + " | " +
                    vehicleType + " | " + costStrategy + " | INR " +
                    String.format("%.2f", totalCost) + " | " + status +
                    " | customer=" + customerUsername + " | rider=" + assignee + " | " + placedAt;
        }
    }
}
