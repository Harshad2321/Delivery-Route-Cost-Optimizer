package com.deliveryroute.model;

/**
 * Runtime vehicle instance created from admin-managed configuration.
 */
public class ConfigurableVehicle extends Vehicle {
    private final String vehicleType;

    public ConfigurableVehicle(String vehicleType,
                               String displayName,
                               double pricePerKm,
                               double maxWeightKg,
                               String mapColor) {
        super("CFG_" + vehicleType.toUpperCase(), displayName, pricePerKm, maxWeightKg, mapColor);
        this.vehicleType = vehicleType;
    }

    @Override
    public String getVehicleType() {
        return vehicleType;
    }
}