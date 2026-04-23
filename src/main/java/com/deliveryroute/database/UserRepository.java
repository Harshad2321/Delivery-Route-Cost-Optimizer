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
                    username VARCHAR(100) NULL UNIQUE,
                    full_name VARCHAR(150) NULL,
                    email VARCHAR(190) NULL UNIQUE,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    account_type VARCHAR(30) NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            // Keep schema backward compatible when app_users table already exists.
            ensureColumn(stmt, "ALTER TABLE app_users MODIFY COLUMN username VARCHAR(100) NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN full_name VARCHAR(150) NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN email VARCHAR(190) NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN is_active TINYINT(1) DEFAULT 1");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN current_lat DOUBLE DEFAULT NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN current_lng DOUBLE DEFAULT NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN last_seen_at DATETIME DEFAULT NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD COLUMN account_type VARCHAR(30) NULL");
            ensureColumn(stmt, "ALTER TABLE app_users ADD UNIQUE KEY uk_app_users_email (email)");

            // Backfill newer account fields for legacy rows.
            stmt.executeUpdate("""
                    UPDATE app_users
                    SET full_name = COALESCE(NULLIF(full_name, ''), username)
                    WHERE full_name IS NULL OR full_name = ''
                    """);
            stmt.executeUpdate("""
                    UPDATE app_users
                    SET email = COALESCE(NULLIF(email, ''), CONCAT(LOWER(username), '@example.local'))
                    WHERE (email IS NULL OR email = '') AND username IS NOT NULL AND username <> ''
                    """);
            stmt.executeUpdate("""
                    UPDATE app_users
                    SET account_type = CASE
                        WHEN UPPER(role) = 'DELIVERY' THEN 'DELIVERY_PARTNER'
                        WHEN UPPER(role) = 'DELIVERY_PARTNER' THEN 'DELIVERY_PARTNER'
                        WHEN UPPER(role) = 'ADMIN' THEN 'ADMIN'
                        ELSE 'CUSTOMER'
                    END
                    WHERE account_type IS NULL OR account_type = ''
                    """);
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

        registerIfMissing("Demo Customer", "customer@drc.local", "customer123", "CUSTOMER");
        registerIfMissing("Demo Delivery", "delivery@drc.local", "delivery123", "DELIVERY_PARTNER");

        registerIfMissing("Admin One", "admin1@drc.local", "Admin@123", "ADMIN");
        registerIfMissing("Admin Two", "admin2@drc.local", "Admin@234", "ADMIN");
        registerIfMissing("Admin Three", "admin3@drc.local", "Admin@345", "ADMIN");
        registerIfMissing("Admin Four", "admin4@drc.local", "Admin@456", "ADMIN");
    }

    private void registerIfMissing(String fullName, String email, String password, String accountType) {
        if (emailExists(email)) {
            return;
        }
        registerUser(fullName, email, password, accountType);
    }

    public boolean usernameExists(String username) {
        return emailExists(username);
    }

    public boolean emailExists(String email) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                SELECT 1 FROM app_users
                WHERE LOWER(COALESCE(email, username)) = LOWER(?)
                LIMIT 1
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking email: " + e.getMessage());
            return false;
        }
    }

    public boolean authenticate(String email, String password, String accountType) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String normalized = normalizeAccountType(accountType);
        String sql = """
                SELECT 1 FROM app_users
                WHERE LOWER(COALESCE(email, username)) = LOWER(?)
                  AND password = ?
                  AND UPPER(COALESCE(NULLIF(account_type, ''), role)) = ?
                  AND is_active = 1
                LIMIT 1
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, password);
            stmt.setString(3, normalized);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error authenticating user: " + e.getMessage());
            return false;
        }
    }

    public boolean registerUser(String username, String password, String role) {
        String normalizedRole = normalizeAccountType(role);
        String generatedEmail = username != null && username.contains("@")
                ? username.trim().toLowerCase()
                : (username == null ? "" : username.trim().toLowerCase() + "@example.local");
        return registerUser(username, generatedEmail, password, normalizedRole);
    }

    public boolean registerUser(String fullName, String email, String password, String accountType) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String normalizedType = normalizeAccountType(accountType);
        String trimmedEmail = email == null ? "" : email.trim().toLowerCase();
        if (trimmedEmail.isBlank()) {
            return false;
        }

        if ("ADMIN".equals(normalizedType)) {
            // Admin accounts are seeded and cannot be self-created.
            return false;
        }

        String safeName = (fullName == null || fullName.isBlank()) ? trimmedEmail : fullName.trim();
        String sql = """
                INSERT INTO app_users (username, full_name, email, password, role, account_type)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trimmedEmail);
            stmt.setString(2, safeName);
            stmt.setString(3, trimmedEmail);
            stmt.setString(4, password);
            stmt.setString(5, normalizedType);
            stmt.setString(6, normalizedType);
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

        String sql = """
                SELECT COALESCE(email, username) AS user_identifier
                FROM app_users
                WHERE UPPER(COALESCE(NULLIF(account_type, ''), role)) = 'DELIVERY_PARTNER'
                  AND is_active = 1
                ORDER BY user_identifier
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(rs.getString("user_identifier"));
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
              SELECT COALESCE(u.email, u.username) AS user_identifier,
                  COALESCE(NULLIF(u.full_name, ''), COALESCE(u.username, u.email)) AS full_name,
                  UPPER(COALESCE(NULLIF(u.account_type, ''), u.role)) AS account_type,
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
                ) o ON o.username = u.username OR o.username = u.email
                GROUP BY user_identifier, full_name, account_type, u.created_at, u.is_active
                ORDER BY u.created_at DESC
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(new UserRecord(
                    rs.getString("user_identifier"),
                    rs.getString("full_name"),
                    rs.getString("account_type"),
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

    public boolean setUserActive(String identifier, boolean active) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                UPDATE app_users
                SET is_active = ?
                WHERE LOWER(COALESCE(email, username)) = LOWER(?)
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, active);
            stmt.setString(2, identifier);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating user active flag: " + e.getMessage());
            return false;
        }
    }

    public boolean updateDeliveryLocation(String identifier, double lat, double lng) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                UPDATE app_users
                SET current_lat = ?, current_lng = ?, last_seen_at = NOW()
                WHERE LOWER(COALESCE(email, username)) = LOWER(?)
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, lat);
            stmt.setDouble(2, lng);
            stmt.setString(3, identifier);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating delivery location: " + e.getMessage());
            return false;
        }
    }

    private String normalizeAccountType(String value) {
        if (value == null) {
            return "CUSTOMER";
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "DELIVERY", "DELIVERY_PARTNER" -> "DELIVERY_PARTNER";
            case "ADMIN" -> "ADMIN";
            default -> "CUSTOMER";
        };
    }

    public record UserRecord(String identifier,
                             String fullName,
                             String accountType,
                             String createdAt,
                             boolean active,
                             int ordersCount) {
    }
}
