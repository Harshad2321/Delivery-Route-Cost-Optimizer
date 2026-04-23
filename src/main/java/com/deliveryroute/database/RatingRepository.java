package com.deliveryroute.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for delivery ratings and reviews.
 */
public class RatingRepository {
    private final DatabaseConnection dbConnection;

    public RatingRepository() {
        this.dbConnection = DatabaseConnection.getInstance();
        ensureTable();
    }

    private void ensureTable() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        String sql = """
                CREATE TABLE IF NOT EXISTS delivery_ratings (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    order_id INT NOT NULL UNIQUE,
                    customer_user VARCHAR(100) NOT NULL,
                    delivery_user VARCHAR(100) NOT NULL,
                    rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
                    review TEXT,
                    rated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (order_id) REFERENCES delivery_orders(id)
                )
                """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error creating delivery_ratings table: " + e.getMessage());
        }
    }

    public boolean addRating(long orderId, String customerUser, String deliveryUser, int rating, String review) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                INSERT INTO delivery_ratings (order_id, customer_user, delivery_user, rating, review)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            stmt.setString(2, customerUser);
            stmt.setString(3, deliveryUser);
            stmt.setInt(4, rating);
            stmt.setString(5, review);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error adding rating: " + e.getMessage());
            return false;
        }
    }

    public double getWeeklyAverageRating() {
        if (!dbConnection.isAvailable()) {
            return 0.0;
        }

        String sql = """
                SELECT COALESCE(AVG(rating), 0)
                FROM delivery_ratings
                WHERE rated_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            System.err.println("Error fetching weekly average rating: " + e.getMessage());
            return 0.0;
        }
    }

    public List<DeliveryRatingPoint> getAverageRatingsByDeliveryUser() {
        List<DeliveryRatingPoint> points = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return points;
        }

        String sql = """
                SELECT delivery_user, COALESCE(AVG(rating), 0) AS avg_rating
                FROM delivery_ratings
                GROUP BY delivery_user
                ORDER BY avg_rating DESC
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                points.add(new DeliveryRatingPoint(
                        rs.getString("delivery_user"),
                        rs.getDouble("avg_rating")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching rating analytics: " + e.getMessage());
        }
        return points;
    }

    public boolean hasRatingForOrder(long orderId) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "SELECT 1 FROM delivery_ratings WHERE order_id = ? LIMIT 1";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking rating existence: " + e.getMessage());
            return false;
        }
    }

    public List<PendingRatingOrder> getPendingRatingsForCustomer(String customerUser) {
        List<PendingRatingOrder> pending = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return pending;
        }

        String sql = """
                SELECT o.id, o.customer_username, o.assigned_delivery_username
                FROM delivery_orders o
                LEFT JOIN delivery_ratings r ON r.order_id = o.id
                WHERE o.customer_username = ?
                  AND o.status = 'DELIVERED'
                  AND o.assigned_delivery_username IS NOT NULL
                  AND r.id IS NULL
                ORDER BY o.delivered_at DESC
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, customerUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pending.add(new PendingRatingOrder(
                            rs.getLong("id"),
                            rs.getString("customer_username"),
                            rs.getString("assigned_delivery_username")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching pending ratings: " + e.getMessage());
        }

        return pending;
    }

    public record PendingRatingOrder(long orderId,
                                     String customerUser,
                                     String deliveryUser) {
    }

    public record DeliveryRatingPoint(String deliveryUser, double averageRating) {
    }
}
