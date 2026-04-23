package com.deliveryroute.cost;

import com.deliveryroute.model.Road;
import com.deliveryroute.model.Vehicle;

/**
 * Standard cost strategy: Fuel + Toll + Traffic penalty
 * Formula: (distance × fuelRate) + toll + (distance × trafficWeight × 0.5)
 */
public class StandardCostStrategy implements CostStrategy {
    
    @Override
    public double calculateEdgeCost(Road road, Vehicle vehicle) {
        double fuelCost = road.getDistanceKm() * vehicle.getFuelRateRupeesPerKm();
        double tollCost = road.getTollChargeRupees();
        double trafficCost = road.getDistanceKm() * road.getTrafficWeight() * 0.5;
        return fuelCost + tollCost + trafficCost;
    }

    @Override
    public String getStrategyName() {
        return "Standard Cost";
    }

    @Override
    public String getDescription() {
        return "Includes fuel, toll charges, and traffic penalties";
    }
}
