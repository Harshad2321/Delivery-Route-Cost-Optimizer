package com.deliveryroute.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for admin-managed configurations used by routing and pricing.
 */
public class AdminConfigRepository {
    private final DatabaseConnection dbConnection;

    public AdminConfigRepository() {
        this.dbConnection = DatabaseConnection.getInstance();
        ensureTables();
        seedDefaults();
    }

    private void ensureTables() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        String vehiclesSql = """
                CREATE TABLE IF NOT EXISTS admin_vehicle_configs (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    vehicle_type VARCHAR(40) NOT NULL UNIQUE,
                    max_weight_kg DOUBLE NOT NULL,
                    max_distance_km DOUBLE NOT NULL,
                    price_per_km DOUBLE NOT NULL,
                    toll_charge DOUBLE NOT NULL,
                    driver_profit DOUBLE NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

        String itemTypesSql = """
                CREATE TABLE IF NOT EXISTS admin_item_types (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(120) NOT NULL UNIQUE,
                    allowed_vehicles VARCHAR(200) NOT NULL,
                    base_price_per_km DOUBLE NOT NULL,
                    toll_threshold_km DOUBLE NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

        String pricingRulesSql = """
                CREATE TABLE IF NOT EXISTS admin_pricing_rules (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    rule_name VARCHAR(120) NOT NULL UNIQUE,
                    base_price_per_km DOUBLE NOT NULL,
                    toll_threshold_km DOUBLE NOT NULL,
                    toll_amount DOUBLE NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(vehiclesSql);
            stmt.execute(itemTypesSql);
            stmt.execute(pricingRulesSql);
        } catch (SQLException e) {
            System.err.println("Error ensuring admin config tables: " + e.getMessage());
        }
    }

    private void seedDefaults() {
        if (!dbConnection.isAvailable()) {
            return;
        }

        upsertVehicleConfig(new VehicleConfig("Bike", 50, 200, 2.0, 15, 20));
        upsertVehicleConfig(new VehicleConfig("Van", 1500, 700, 5.5, 45, 22));
        upsertVehicleConfig(new VehicleConfig("Truck", 5000, 1200, 8.5, 90, 25));

        upsertItemType(new ItemTypeConfig("Documents", "Bike,Van", 1.8, 50));
        upsertItemType(new ItemTypeConfig("Medicines", "Bike,Van", 2.2, 40));
        upsertItemType(new ItemTypeConfig("Furniture", "Van,Truck", 4.8, 60));
        upsertItemType(new ItemTypeConfig("Industrial Goods", "Truck", 6.5, 80));

        upsertPricingRule(new PricingRuleConfig("Default Toll Rule", 2.0, 50, 35));
        upsertPricingRule(new PricingRuleConfig("Long Haul Toll Rule", 5.0, 150, 80));
    }

    public List<VehicleConfig> getVehicleConfigs() {
        List<VehicleConfig> result = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return result;
        }

        String sql = """
                SELECT vehicle_type, max_weight_kg, max_distance_km, price_per_km, toll_charge, driver_profit
                FROM admin_vehicle_configs
                ORDER BY vehicle_type
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(new VehicleConfig(
                        rs.getString("vehicle_type"),
                        rs.getDouble("max_weight_kg"),
                        rs.getDouble("max_distance_km"),
                        rs.getDouble("price_per_km"),
                        rs.getDouble("toll_charge"),
                        rs.getDouble("driver_profit")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching vehicle configs: " + e.getMessage());
        }

        return result;
    }

    public VehicleConfig getVehicleConfigByType(String vehicleType) {
        if (!dbConnection.isAvailable()) {
            return null;
        }

        String sql = """
                SELECT vehicle_type, max_weight_kg, max_distance_km, price_per_km, toll_charge, driver_profit
                FROM admin_vehicle_configs
                WHERE UPPER(vehicle_type) = UPPER(?)
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, vehicleType);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new VehicleConfig(
                            rs.getString("vehicle_type"),
                            rs.getDouble("max_weight_kg"),
                            rs.getDouble("max_distance_km"),
                            rs.getDouble("price_per_km"),
                            rs.getDouble("toll_charge"),
                            rs.getDouble("driver_profit")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching vehicle config by type: " + e.getMessage());
        }

        return null;
    }

    public boolean upsertVehicleConfig(VehicleConfig config) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                INSERT INTO admin_vehicle_configs (
                    vehicle_type, max_weight_kg, max_distance_km, price_per_km, toll_charge, driver_profit
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    max_weight_kg = VALUES(max_weight_kg),
                    max_distance_km = VALUES(max_distance_km),
                    price_per_km = VALUES(price_per_km),
                    toll_charge = VALUES(toll_charge),
                    driver_profit = VALUES(driver_profit)
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, config.vehicleType());
            stmt.setDouble(2, config.maxWeightKg());
            stmt.setDouble(3, config.maxDistanceKm());
            stmt.setDouble(4, config.pricePerKm());
            stmt.setDouble(5, config.tollCharge());
            stmt.setDouble(6, config.driverProfit());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error upserting vehicle config: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteVehicleConfig(String vehicleType) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "DELETE FROM admin_vehicle_configs WHERE UPPER(vehicle_type) = UPPER(?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, vehicleType);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting vehicle config: " + e.getMessage());
            return false;
        }
    }

    public List<ItemTypeConfig> getItemTypes() {
        List<ItemTypeConfig> result = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return result;
        }

        String sql = """
                SELECT name, allowed_vehicles, base_price_per_km, toll_threshold_km
                FROM admin_item_types
                ORDER BY name
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(new ItemTypeConfig(
                        rs.getString("name"),
                        rs.getString("allowed_vehicles"),
                        rs.getDouble("base_price_per_km"),
                        rs.getDouble("toll_threshold_km")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching item type configs: " + e.getMessage());
        }

        return result;
    }

    public boolean upsertItemType(ItemTypeConfig config) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                INSERT INTO admin_item_types (name, allowed_vehicles, base_price_per_km, toll_threshold_km)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    allowed_vehicles = VALUES(allowed_vehicles),
                    base_price_per_km = VALUES(base_price_per_km),
                    toll_threshold_km = VALUES(toll_threshold_km)
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, config.name());
            stmt.setString(2, config.allowedVehicles());
            stmt.setDouble(3, config.basePricePerKm());
            stmt.setDouble(4, config.tollThresholdKm());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error upserting item type config: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteItemType(String name) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "DELETE FROM admin_item_types WHERE UPPER(name) = UPPER(?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting item type config: " + e.getMessage());
            return false;
        }
    }

    public List<PricingRuleConfig> getPricingRules() {
        List<PricingRuleConfig> result = new ArrayList<>();
        if (!dbConnection.isAvailable()) {
            return result;
        }

        String sql = """
                SELECT rule_name, base_price_per_km, toll_threshold_km, toll_amount
                FROM admin_pricing_rules
                ORDER BY rule_name
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.add(new PricingRuleConfig(
                        rs.getString("rule_name"),
                        rs.getDouble("base_price_per_km"),
                        rs.getDouble("toll_threshold_km"),
                        rs.getDouble("toll_amount")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching pricing rules: " + e.getMessage());
        }

        return result;
    }

    public boolean upsertPricingRule(PricingRuleConfig config) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = """
                INSERT INTO admin_pricing_rules (rule_name, base_price_per_km, toll_threshold_km, toll_amount)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    base_price_per_km = VALUES(base_price_per_km),
                    toll_threshold_km = VALUES(toll_threshold_km),
                    toll_amount = VALUES(toll_amount)
                """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, config.ruleName());
            stmt.setDouble(2, config.basePricePerKm());
            stmt.setDouble(3, config.tollThresholdKm());
            stmt.setDouble(4, config.tollAmount());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error upserting pricing rule: " + e.getMessage());
            return false;
        }
    }

    public boolean deletePricingRule(String ruleName) {
        if (!dbConnection.isAvailable()) {
            return false;
        }

        String sql = "DELETE FROM admin_pricing_rules WHERE UPPER(rule_name) = UPPER(?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ruleName);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting pricing rule: " + e.getMessage());
            return false;
        }
    }

    public record VehicleConfig(String vehicleType,
                                double maxWeightKg,
                                double maxDistanceKm,
                                double pricePerKm,
                                double tollCharge,
                                double driverProfit) {
    }

    public record ItemTypeConfig(String name,
                                 String allowedVehicles,
                                 double basePricePerKm,
                                 double tollThresholdKm) {
    }

    public record PricingRuleConfig(String ruleName,
                                    double basePricePerKm,
                                    double tollThresholdKm,
                                    double tollAmount) {
    }
}