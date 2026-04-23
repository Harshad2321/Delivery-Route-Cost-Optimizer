package com.deliveryroute.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.deliveryroute.model.Route;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Repository for persisting and retrieving routes from database
 */
public class RouteRepository {
    private final DatabaseConnection dbConnection;
    private final ObjectMapper objectMapper;

    public RouteRepository() {
        this.dbConnection = DatabaseConnection.getInstance();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Save a calculated route to database
     */
    public void saveRoute(Route route, String sourceCity, String destinationCity) {
        if (!dbConnection.isAvailable()) {
            System.err.println("Database not available, skipping save");
            return;
        }

        String sql = "INSERT INTO saved_routes (source_city, destination_city, vehicle_type, cost_strategy, " +
            "path_cities, total_cost, fuel_cost, toll_cost, traffic_penalty, " +
            "total_distance_km, calculated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String pathCitiesJson = objectMapper.writeValueAsString(route.getCityPath());

            stmt.setString(1, sourceCity);
            stmt.setString(2, destinationCity);
            stmt.setString(3, route.getVehicleType());
            stmt.setString(4, route.getCostStrategy());
            stmt.setString(5, pathCitiesJson);
            stmt.setDouble(6, route.getTotalCost());
            stmt.setDouble(7, route.getTotalFuelCost());
            stmt.setDouble(8, route.getTotalTollCost());
            stmt.setDouble(9, route.getTotalTrafficPenalty());
            stmt.setDouble(10, route.getTotalDistanceKm());
            stmt.setString(11, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving route: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error serializing route: " + e.getMessage());
        }
    }

    /**
     * Get all saved routes from database
     */
    public List<RouteRecord> getAllRoutes() {
        List<RouteRecord> routes = new ArrayList<>();
        
        if (!dbConnection.isAvailable()) {
            System.err.println("Database not available");
            return routes;
        }

        String sql = "SELECT id, source_city, destination_city, vehicle_type, cost_strategy, " +
                "total_cost, total_distance_km, calculated_at FROM saved_routes ORDER BY calculated_at DESC";

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                routes.add(new RouteRecord(
                    rs.getLong("id"),
                    rs.getString("source_city"),
                    rs.getString("destination_city"),
                    rs.getString("vehicle_type"),
                    rs.getString("cost_strategy"),
                    rs.getDouble("total_cost"),
                    rs.getDouble("total_distance_km"),
                    rs.getString("calculated_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving routes: " + e.getMessage());
        }

        return routes;
    }

    /**
     * Inner class representing a route record from database
     */
    public static class RouteRecord {
        private final long id;
        private final String sourceCity;
        private final String destinationCity;
        private final String vehicleType;
        private final String costStrategy;
        private final double totalCost;
        private final double totalDistanceKm;
        private final String calculatedAt;

        public RouteRecord(long id, String sourceCity, String destinationCity, String vehicleType,
                          String costStrategy, double totalCost, double totalDistanceKm, String calculatedAt) {
            this.id = id;
            this.sourceCity = sourceCity;
            this.destinationCity = destinationCity;
            this.vehicleType = vehicleType;
            this.costStrategy = costStrategy;
            this.totalCost = totalCost;
            this.totalDistanceKm = totalDistanceKm;
            this.calculatedAt = calculatedAt;
        }

        public long getId() { return id; }
        public String getSourceCity() { return sourceCity; }
        public String getDestinationCity() { return destinationCity; }
        public String getVehicleType() { return vehicleType; }
        public String getCostStrategy() { return costStrategy; }
        public double getTotalCost() { return totalCost; }
        public double getTotalDistanceKm() { return totalDistanceKm; }
        public String getCalculatedAt() { return calculatedAt; }

        @Override
        public String toString() {
            return sourceCity + " → " + destinationCity + " (" + vehicleType + ") - ₹" + 
                    String.format("%.2f", totalCost) + " | " + calculatedAt;
        }
    }
}
