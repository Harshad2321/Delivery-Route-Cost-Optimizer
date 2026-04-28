package com.deliveryroute.model;

import java.time.LocalDateTime;

/**
 * Represents a delivery rider/partner managing deliveries.
 */
public class Rider {
    private final long id;
    private final String name;
    private final String phoneNumber;
    private final String vehicleType;  // Bike, Van, Truck
    private final String availability; // AVAILABLE, BUSY, OFF_DUTY
    private final String currentCity;
    private final double currentLat;
    private final double currentLng;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Rider(long id, String name, String phoneNumber, String vehicleType,
                 String availability, String currentCity, double currentLat, double currentLng,
                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.vehicleType = vehicleType;
        this.availability = availability;
        this.currentCity = currentCity;
        this.currentLat = currentLat;
        this.currentLng = currentLng;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public String getAvailability() {
        return availability;
    }

    public String getCurrentCity() {
        return currentCity;
    }

    public double getCurrentLat() {
        return currentLat;
    }

    public double getCurrentLng() {
        return currentLng;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Rider{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", vehicleType='" + vehicleType + '\'' +
                ", availability='" + availability + '\'' +
                ", currentCity='" + currentCity + '\'' +
                '}';
    }
}
