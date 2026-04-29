package com.deliveryroute.model;

/**
 * Bike vehicle type - lightweight delivery for urban areas.
 * Fuel rate: ₹2.0 per km
 * Capacity: 10 kg
 * Map color: Green
 */
public class Bike extends Vehicle {
    public Bike() {
        super("BIKE_001", "🏍 Bike", 2.0, 10.0, "#22C55E");
    }

    @Override
    public String getVehicleType() {
        return "Bike";
    }
}
