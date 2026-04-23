package com.deliveryroute.cost;

import com.deliveryroute.model.Road;
import com.deliveryroute.model.Vehicle;

/**
 * Toll-free cost strategy: Fuel + higher traffic penalty (avoids toll roads)
 * Formula: (distance × fuelRate) + (distance × trafficWeight × 3.0)
 * High traffic multiplier discourages toll roads by inflating their cost
 */
public class TollFreeCostStrategy implements CostStrategy {
    
    @Override
    public double calculateEdgeCost(Road road, Vehicle vehicle) {
        double fuelCost = road.getDistanceKm() * vehicle.getFuelRateRupeesPerKm();
        // Apply higher traffic multiplier to avoid toll roads (toll roads often have heavy traffic)
        double trafficCost = road.getDistanceKm() * road.getTrafficWeight() * 3.0;
        return fuelCost + trafficCost;
    }

    @Override
    public String getStrategyName() {
        return "Toll-Free Route";
    }

    @Override
    public String getDescription() {
        return "Minimizes toll charges by avoiding toll roads (may take longer)";
    }
}
