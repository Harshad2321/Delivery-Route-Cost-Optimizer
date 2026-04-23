package com.deliveryroute.model;

import java.util.Objects;

/**
 * Represents a city in the delivery network.
 * Each city has a unique ID, name, and latitude/longitude for map display.
 */
public class City {
    private final int id;
    private final String name;
    private final double latitude;
    private final double longitude;

    public City(int id, String name, double latitude, double longitude) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "City name cannot be null");
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        City city = (City) o;
        return id == city.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
