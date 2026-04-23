package com.deliveryroute.model;

import java.util.Objects;

/**
 * Abstract base class for vehicles.
 * Subclasses: Bike, Van, Truck
 */
public abstract class Vehicle {
    private final String vehicleId;
    private final String displayName;
    private final double fuelRateRupeesPerKm;
    private final double capacityKg;
    private final String mapColor;

    protected Vehicle(String vehicleId, String displayName, double fuelRateRupeesPerKm, 
                     double capacityKg, String mapColor) {
        this.vehicleId = Objects.requireNonNull(vehicleId, "Vehicle ID cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
        if (fuelRateRupeesPerKm <= 0) {
            throw new IllegalArgumentException("Fuel rate must be positive");
        }
        this.fuelRateRupeesPerKm = fuelRateRupeesPerKm;
        this.capacityKg = capacityKg;
        this.mapColor = Objects.requireNonNull(mapColor, "Map color cannot be null");
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getFuelRateRupeesPerKm() {
        return fuelRateRupeesPerKm;
    }

    public double getCapacityKg() {
        return capacityKg;
    }

    public String getMapColor() {
        return mapColor;
    }

    public abstract String getVehicleType();

    @Override
    public String toString() {
        return displayName;
    }
}
