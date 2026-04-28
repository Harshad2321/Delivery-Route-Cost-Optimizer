package com.deliveryroute.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.deliveryroute.model.Rider;

/**
 * Repository for delivery riders management.
 */
public class RiderRepository {
    private final DatabaseConnection dbConnection;

    public RiderRepository() {
        this.dbConnection = DatabaseConnection.getInstance();
        ensureRiderTable();
    }

    private void ensureRiderTable() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        String sql = """
                CREATE TABLE IF NOT EXISTS riders (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    phone_number VARCHAR(20) NOT NULL UNIQUE,
                    vehicle_type VARCHAR(50) NOT NULL,
                    availability VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
                    current_city VARCHAR(100) NOT NULL,
                    current_lat DOUBLE DEFAULT 0,
                    current_lng DOUBLE DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT chk_vehicle_type CHECK (vehicle_type IN ('Bike', 'Van', 'Truck')),
                    CONSTRAINT chk_availability CHECK (availability IN ('AVAILABLE', 'BUSY', 'OFF_DUTY')),
                    INDEX idx_availability (availability),
                    INDEX idx_vehicle_type (vehicle_type),
                    INDEX idx_current_city (current_city)
                )
                """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error ensuring rider table: " + e.getMessage());
        }
    }

    /**
     * Create a new rider
     */
    public long createRider(String name, String phoneNumber, String vehicleType, String currentCity) {
        if (!dbConnection.isAvailable()) {
            throw new RuntimeException("Database unavailable. Cannot create rider.");
        }

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Rider name cannot be empty");
        }
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }
        if (!List.of("Bike", "Van", "Truck").contains(vehicleType)) {
            throw new IllegalArgumentException("Invalid vehicle type");
        }

        String sql = """
                INSERT INTO riders (name, phone_number, vehicle_type, current_city, availability)
                VALUES (?, ?, ?, ?, 'AVAILABLE')
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name.trim());
            stmt.setString(2, phoneNumber.trim());
            stmt.setString(3, vehicleType);
            stmt.setString(4, currentCity.trim());
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create rider: " + e.getMessage(), e);
        }

        throw new RuntimeException("Failed to generate rider ID");
    }

    /**
     * Get all riders
     */
    public List<Rider> getAllRiders() {
        List<Rider> riders = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return riders;
        }

        String sql = "SELECT * FROM riders ORDER BY name ASC";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                riders.add(mapRowToRider(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all riders: " + e.getMessage());
        }

        return riders;
    }

    /**
     * Get rider by ID
     */
    public Rider getRiderById(long riderId) {
        if (!dbConnection.isAvailable()) {
            return null;
        }

        String sql = "SELECT * FROM riders WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, riderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToRider(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching rider: " + e.getMessage());
        }

        return null;
    }

    /**
     * Get available riders by vehicle type
     */
    public List<Rider> getAvailableRidersByVehicle(String vehicleType) {
        List<Rider> riders = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return riders;
        }

        String sql = "SELECT * FROM riders WHERE vehicle_type = ? AND availability = 'AVAILABLE' ORDER BY name ASC";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, vehicleType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    riders.add(mapRowToRider(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching available riders: " + e.getMessage());
        }

        return riders;
    }

    /**
     * Update rider information
     */
    public boolean updateRider(long riderId, String name, String phoneNumber, String vehicleType,
                               String availability, String currentCity, double currentLat, double currentLng) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                UPDATE riders
                SET name = ?, phone_number = ?, vehicle_type = ?, availability = ?,
                    current_city = ?, current_lat = ?, current_lng = ?
                WHERE id = ?
                """;

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name.trim());
            stmt.setString(2, phoneNumber.trim());
            stmt.setString(3, vehicleType);
            stmt.setString(4, availability);
            stmt.setString(5, currentCity.trim());
            stmt.setDouble(6, currentLat);
            stmt.setDouble(7, currentLng);
            stmt.setLong(8, riderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating rider: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update rider availability
     */
    public boolean updateAvailability(long riderId, String availability) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        if (!List.of("AVAILABLE", "BUSY", "OFF_DUTY").contains(availability)) {
            return false;
        }

        String sql = "UPDATE riders SET availability = ? WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, availability);
            stmt.setLong(2, riderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating rider availability: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update rider location
     */
    public boolean updateLocation(long riderId, double lat, double lng, String currentCity) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "UPDATE riders SET current_lat = ?, current_lng = ?, current_city = ? WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, lat);
            stmt.setDouble(2, lng);
            stmt.setString(3, currentCity.trim());
            stmt.setLong(4, riderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating rider location: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete rider
     */
    public boolean deleteRider(long riderId) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "DELETE FROM riders WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, riderId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting rider: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get rider count
     */
    public long getRiderCount() {
        if (!dbConnection.isAvailable()) {
            return 0;
        }

        String sql = "SELECT COUNT(*) as count_value FROM riders";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("count_value");
            }
        } catch (SQLException e) {
            System.err.println("Error fetching rider count: " + e.getMessage());
        }

        return 0;
    }

    private Rider mapRowToRider(ResultSet rs) throws SQLException {
        return new Rider(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("phone_number"),
                rs.getString("vehicle_type"),
                rs.getString("availability"),
                rs.getString("current_city"),
                rs.getDouble("current_lat"),
                rs.getDouble("current_lng"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null
        );
    }
}
