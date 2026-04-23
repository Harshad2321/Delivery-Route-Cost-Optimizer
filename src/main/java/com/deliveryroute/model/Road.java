package com.deliveryroute.model;

import java.util.Objects;

/**
 * Represents a bidirectional road between two cities.
 * Immutable - all fields set at construction.
 * Contains distance, toll charges, and traffic weight information.
 */
public class Road {
    private final City source;
    private final City destination;
    private final double distanceKm;
    private final double tollChargeRupees;
    private final double trafficWeight;

    public Road(City source, City destination, double distanceKm, double tollChargeRupees, double trafficWeight) {
        this.source = Objects.requireNonNull(source, "Source city cannot be null");
        this.destination = Objects.requireNonNull(destination, "Destination city cannot be null");
        if (distanceKm <= 0) {
            throw new IllegalArgumentException("Distance must be positive");
        }
        this.distanceKm = distanceKm;
        this.tollChargeRupees = Math.max(0, tollChargeRupees);
        this.trafficWeight = trafficWeight;
    }

    public City getSource() {
        return source;
    }

    public City getDestination() {
        return destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public double getTollChargeRupees() {
        return tollChargeRupees;
    }

    public double getTrafficWeight() {
        return trafficWeight;
    }

    @Override
    public String toString() {
        return source.getName() + " -> " + destination.getName() + 
               " (" + String.format("%.1f", distanceKm) + " km)";
    }
}
