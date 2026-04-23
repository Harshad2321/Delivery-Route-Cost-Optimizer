package com.deliveryroute.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for delivery tracking updates.
 */
public class TrackingRepository {
    private final DatabaseConnection dbConnection;

    public TrackingRepository() {
        this.dbConnection = DatabaseConnection.getInstance();
        ensureTable();
    }

    private void ensureTable() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        String sql = """
                CREATE TABLE IF NOT EXISTS tracking_updates (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_id INT NOT NULL,
                    delivery_user VARCHAR(100) NOT NULL,
                    lat DOUBLE NOT NULL,
                    lng DOUBLE NOT NULL,
                    recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_order (order_id),
                    FOREIGN KEY (order_id) REFERENCES delivery_orders(id) ON DELETE CASCADE
                )
                """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error creating tracking_updates table: " + e.getMessage());
        }
    }

    public boolean addUpdate(long orderId, String deliveryUser, double lat, double lng) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "INSERT INTO tracking_updates (order_id, delivery_user, lat, lng) VALUES (?, ?, ?, ?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            stmt.setString(2, deliveryUser);
            stmt.setDouble(3, lat);
            stmt.setDouble(4, lng);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error inserting tracking update: " + e.getMessage());
            return false;
        }
    }

    public TrackingUpdate getLatestUpdate(long orderId) {
        if (!dbConnection.isAvailable()) {
            return null;
        }

        String sql = """
                SELECT id, order_id, delivery_user, lat, lng, recorded_at
                FROM tracking_updates
                WHERE order_id = ?
                ORDER BY recorded_at DESC, id DESC
                LIMIT 1
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new TrackingUpdate(
                            rs.getLong("id"),
                            rs.getLong("order_id"),
                            rs.getString("delivery_user"),
                            rs.getDouble("lat"),
                            rs.getDouble("lng"),
                            rs.getString("recorded_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching latest tracking update: " + e.getMessage());
        }
        return null;
    }

    public List<TrackingUpdate> getUpdatesForOrder(long orderId) {
        List<TrackingUpdate> updates = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return updates;
        }

        String sql = """
                SELECT id, order_id, delivery_user, lat, lng, recorded_at
                FROM tracking_updates
                WHERE order_id = ?
                ORDER BY recorded_at ASC, id ASC
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    updates.add(new TrackingUpdate(
                            rs.getLong("id"),
                            rs.getLong("order_id"),
                            rs.getString("delivery_user"),
                            rs.getDouble("lat"),
                            rs.getDouble("lng"),
                            rs.getString("recorded_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching tracking updates: " + e.getMessage());
        }

        return updates;
    }

    public record TrackingUpdate(long id,
                                 long orderId,
                                 String deliveryUser,
                                 double lat,
                                 double lng,
                                 String recordedAt) {
    }
}
