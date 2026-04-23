package com.deliveryroute.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for basic user authentication and registration.
 */
public class UserRepository {
    private final DatabaseConnection dbConnection;

    public UserRepository() {
        this.dbConnection = DatabaseConnection.getInstance();
        ensureUserTable();
        seedDefaultUsers();
    }

    private void ensureUserTable() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        String sql = """
                CREATE TABLE IF NOT EXISTS app_users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(100) NOT NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            // Keep schema backward compatible when app_users table already exists.
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN is_active TINYINT(1) DEFAULT 1");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN current_lat DOUBLE DEFAULT NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN current_lng DOUBLE DEFAULT NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN last_seen_at DATETIME DEFAULT NULL");
        } catch (SQLException e) {
            System.err.println("Error creating app_users table: " + e.getMessage());
        }
    }

    private void ensureColumn(Statement stmt, String alterSql) {
        try {
            stmt.execute(alterSql);
        } catch (SQLException ignored) {
            // Ignore duplicate-column errors for idempotent startup.
        }
    }

    private void seedDefaultUsers() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        registerIfMissing("customer", "customer123", "CUSTOMER");
        registerIfMissing("admin", "admin123", "ADMIN");
        registerIfMissing("delivery", "delivery123", "DELIVERY");
    }

    private void registerIfMissing(String username, String password, String role) {
        if (usernameExists(username)) {
            return;
        }
        registerUser(username, password, role);
    }

    public boolean usernameExists(String username) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "SELECT 1 FROM app_users WHERE LOWER(username) = LOWER(?) LIMIT 1";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking username: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticate(String username, String password, String role) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                SELECT 1 FROM app_users
                WHERE LOWER(username) = LOWER(?)
                  AND password = ?
                  AND role = ?
                                    AND is_active = 1
                LIMIT 1
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, role);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error authenticating user: " + e.getMessage());
            return false;
        }
    }

    public boolean registerUser(String username, String password, String role) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "INSERT INTO app_users (username, password, role) VALUES (?, ?, ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, role);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error registering user: " + e.getMessage());
            return false;
        }
    }

    public List<String> getActiveDeliveryUsers() {
        List<String> users = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return users;
        }

        String sql = "SELECT username FROM app_users WHERE role = 'DELIVERY' AND is_active = 1 ORDER BY username";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching active delivery users: " + e.getMessage());
        }
        return users;
    }

    public List<UserRecord> getAllUsersWithOrderCount() {
        List<UserRecord> users = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return users;
        }

        String sql = """
                SELECT u.username,
                       u.role,
                       u.created_at,
                       u.is_active,
                      COALESCE(SUM(o.order_count), 0) AS orders_count
                FROM app_users u
                LEFT JOIN (
                    SELECT customer_username AS username, COUNT(*) AS order_count
                    FROM delivery_orders
                    GROUP BY customer_username
                    UNION ALL
                    SELECT assigned_delivery_username AS username, COUNT(*) AS order_count
                    FROM delivery_orders
                    WHERE assigned_delivery_username IS NOT NULL
                    GROUP BY assigned_delivery_username
                ) o ON o.username = u.username
                GROUP BY u.username, u.role, u.created_at, u.is_active
                ORDER BY u.created_at DESC
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(new UserRecord(
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getString("created_at"),
                        rs.getBoolean("is_active"),
                        rs.getInt("orders_count")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching users: " + e.getMessage());
        }

        return users;
    }

    public boolean setUserActive(String username, boolean active) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "UPDATE app_users SET is_active = ? WHERE username = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, active);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating user active flag: " + e.getMessage());
            return false;
        }
    }

    public boolean updateDeliveryLocation(String username, double lat, double lng) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                UPDATE app_users
                SET current_lat = ?, current_lng = ?, last_seen_at = NOW()
                WHERE username = ?
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, lat);
            stmt.setDouble(2, lng);
            stmt.setString(3, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating delivery location: " + e.getMessage());
            return false;
        }
    }

    public record UserRecord(String username,
                             String role,
                             String createdAt,
                             boolean active,
                             int ordersCount) {
    }
}
