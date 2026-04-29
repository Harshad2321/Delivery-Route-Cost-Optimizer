package com.deliveryroute.model;

/**
 * Truck vehicle type - heavy duty long-distance delivery.
 * Fuel rate: ₹8.5 per km
 * Capacity: 1000 kg
 * Map color: Red
 */
public class Truck extends Vehicle {
    public Truck() {
        super("TRUCK_001", "🚛 Truck", 8.5, 1000.0, "#EF4444");
    }

    @Override
    public String getVehicleType() {
        return "Truck";
    }
}
