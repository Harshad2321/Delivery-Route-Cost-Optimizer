package com.deliveryroute.cost;

import com.deliveryroute.model.Road;
import com.deliveryroute.model.Vehicle;

/**
 * Interface for different cost calculation strategies.
 */
public interface CostStrategy {
    /**
     * Calculate the cost of traversing a road with a specific vehicle.
     */
    double calculateEdgeCost(Road road, Vehicle vehicle);

    /**
     * Get the strategy name for display.
     */
    String getStrategyName();

    /**
     * Get the strategy description.
     */
    String getDescription();
}
