package com.deliveryroute.model;

/**
 * Van vehicle type - medium delivery vehicle.
 * Fuel rate: ₹5.5 per km
 * Capacity: 100 kg
 * Map color: Blue
 */
public class Van extends Vehicle {
    public Van() {
        super("VAN_001", "🚐 Van", 5.5, 100.0, "#3B82F6");
    }

    @Override
    public String getVehicleType() {
        return "Van";
    }
}
