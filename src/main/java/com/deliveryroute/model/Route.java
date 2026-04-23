package com.deliveryroute.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Represents a calculated optimal route with cost breakdown.
 */
public class Route {
    private final List<City> cityPath;
    private final String vehicleType;
    private final String costStrategy;
    private final double totalCost;
    private final double totalFuelCost;
    private final double totalTollCost;
    private final double totalTrafficPenalty;
    private final double totalDistanceKm;
    private final LocalDateTime calculatedAt;

    public Route(List<City> cityPath, String vehicleType, String costStrategy, 
                 double totalCost, double totalFuelCost, 
                 double totalTollCost, double totalTrafficPenalty, double totalDistanceKm) {
        this.cityPath = cityPath;
        this.vehicleType = vehicleType;
        this.costStrategy = costStrategy;
        this.totalCost = totalCost;
        this.totalFuelCost = totalFuelCost;
        this.totalTollCost = totalTollCost;
        this.totalTrafficPenalty = totalTrafficPenalty;
        this.totalDistanceKm = totalDistanceKm;
        this.calculatedAt = LocalDateTime.now();
    }

    public List<City> getCityPath() {
        return cityPath;
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

    public double getTotalFuelCost() {
        return totalFuelCost;
    }

    public double getTotalTollCost() {
        return totalTollCost;
    }

    public double getTotalTrafficPenalty() {
        return totalTrafficPenalty;
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public String getFormattedPath() {
        return String.join(" → ", cityPath.stream().map(City::getName).toArray(String[]::new));
    }

    public String getCostSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Route: ").append(getFormattedPath()).append("\n");
        sb.append("Vehicle: ").append(vehicleType).append("\n");
        sb.append("Strategy: ").append(costStrategy).append("\n");
        sb.append("Distance: ").append(String.format("%.2f", totalDistanceKm)).append(" km\n");
        sb.append("Fuel Cost: ₹").append(String.format("%.2f", totalFuelCost)).append("\n");
        sb.append("Toll Cost: ₹").append(String.format("%.2f", totalTollCost)).append("\n");
        sb.append("Traffic Penalty: ₹").append(String.format("%.2f", totalTrafficPenalty)).append("\n");
        sb.append("TOTAL COST: ₹").append(String.format("%.2f", totalCost));
        return sb.toString();
    }

    public double[][] getLatLngList() {
        double[][] coords = new double[cityPath.size()][2];
        for (int i = 0; i < cityPath.size(); i++) {
            coords[i][0] = cityPath.get(i).getLatitude();
            coords[i][1] = cityPath.get(i).getLongitude();
        }
        return coords;
    }

    @Override
    public String toString() {
        return getFormattedPath() + " - ₹" + String.format("%.2f", totalCost);
    }
}
