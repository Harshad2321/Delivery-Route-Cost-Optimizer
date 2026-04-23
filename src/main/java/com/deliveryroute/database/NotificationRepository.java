package com.deliveryroute.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for in-app notifications.
 */
public class NotificationRepository {
    private final DatabaseConnection dbConnection;

    public NotificationRepository() {
        this.dbConnection = DatabaseConnection.getInstance();
        ensureTable();
    }

    private void ensureTable() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        String sql = """
                CREATE TABLE IF NOT EXISTS notifications (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    target_user VARCHAR(100) NOT NULL,
                    message TEXT NOT NULL,
                    is_read TINYINT(1) DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_user_unread (target_user, is_read)
                )
                """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error creating notifications table: " + e.getMessage());
        }
    }

    public boolean addNotification(String targetUser, String message) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "INSERT INTO notifications (target_user, message, is_read) VALUES (?, ?, 0)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUser);
            stmt.setString(2, message);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error adding notification: " + e.getMessage());
            return false;
        }
    }

    public List<NotificationRecord> getNotifications(String targetUser, boolean unreadOnly) {
        List<NotificationRecord> notifications = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return notifications;
        }

        String sql = unreadOnly
                ? "SELECT id, target_user, message, is_read, created_at FROM notifications WHERE target_user = ? AND is_read = 0 ORDER BY created_at DESC"
                : "SELECT id, target_user, message, is_read, created_at FROM notifications WHERE target_user = ? ORDER BY created_at DESC";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUser);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    notifications.add(new NotificationRecord(
                            rs.getLong("id"),
                            rs.getString("target_user"),
                            rs.getString("message"),
                            rs.getBoolean("is_read"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching notifications: " + e.getMessage());
        }

        return notifications;
    }

    public List<NotificationRecord> getLatestNotifications(String targetUser, int limit) {
        List<NotificationRecord> notifications = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return notifications;
        }

        String sql = """
                SELECT id, target_user, message, is_read, created_at
                FROM notifications
                WHERE target_user = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUser);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    notifications.add(new NotificationRecord(
                            rs.getLong("id"),
                            rs.getString("target_user"),
                            rs.getString("message"),
                            rs.getBoolean("is_read"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching latest notifications: " + e.getMessage());
        }

        return notifications;
    }

    public boolean markRead(long notificationId) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "UPDATE notifications SET is_read = 1 WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, notificationId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error marking notification read: " + e.getMessage());
            return false;
        }
    }

    public long countUnread(String targetUser) {
        if (!dbConnection.isAvailable()) {
            return 0L;
        }

        String sql = "SELECT COUNT(*) FROM notifications WHERE target_user = ? AND is_read = 0";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUser);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            System.err.println("Error counting unread notifications: " + e.getMessage());
            return 0L;
        }
    }

    public boolean markAllRead(String targetUser) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "UPDATE notifications SET is_read = 1 WHERE target_user = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUser);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error marking all notifications read: " + e.getMessage());
            return false;
        }
    }

    public record NotificationRecord(long id,
                                     String targetUser,
                                     String message,
                                     boolean isRead,
                                     String createdAt) {
    }
}
