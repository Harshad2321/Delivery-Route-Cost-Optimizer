package com.deliveryroute.algorithm;

import com.deliveryroute.model.City;
import com.deliveryroute.model.Road;

import java.util.*;

/**
 * Graph representation of cities and roads for route optimization.
 * Uses adjacency list with bidirectional edges.
 */
public class Graph {
    private final List<City> cities;
    private final Map<Integer, List<Road>> adjacencyList;

    public Graph() {
        this.cities = new ArrayList<>();
        this.adjacencyList = new HashMap<>();
    }

    public void addCity(City city) {
        if (!cities.contains(city)) {
            cities.add(city);
            adjacencyList.putIfAbsent(city.getId(), new ArrayList<>());
        }
    }

    public void addRoad(Road road) {
        adjacencyList.computeIfAbsent(road.getSource().getId(), k -> new ArrayList<>()).add(road);
        adjacencyList.computeIfAbsent(road.getDestination().getId(), k -> new ArrayList<>())
                .add(new Road(road.getDestination(), road.getSource(), road.getDistanceKm(), 
                        road.getTollChargeRupees(), road.getTrafficWeight()));
    }

    public List<City> getCities() {
        return new ArrayList<>(cities);
    }

    public List<Road> getRoadsFrom(City city) {
        return new ArrayList<>(adjacencyList.getOrDefault(city.getId(), new ArrayList<>()));
    }

    public City getCityById(int cityId) {
        return cities.stream().filter(c -> c.getId() == cityId).findFirst().orElse(null);
    }

    /**
     * Build demo graph with 8 Indian cities and 14 roads
     */
    public static Graph buildDemoGraph() {
        Graph graph = new Graph();

        // Create 8 Indian cities with lat/lng coordinates
        City mumbai = new City(1, "Mumbai", 19.0760, 72.8777);
        City pune = new City(2, "Pune", 18.5204, 73.8567);
        City aurangabad = new City(3, "Aurangabad", 19.8762, 75.3433);
        City nashik = new City(4, "Nashik", 19.9975, 73.7898);
        City nagpur = new City(5, "Nagpur", 21.1458, 79.0882);
        City ahmedabad = new City(6, "Ahmedabad", 23.0225, 72.5714);
        City indore = new City(7, "Indore", 22.7196, 75.8577);
        City bhopal = new City(8, "Bhopal", 23.1815, 79.9864);

        // Add cities
        for (City city : Arrays.asList(mumbai, pune, aurangabad, nashik, nagpur, ahmedabad, indore, bhopal)) {
            graph.addCity(city);
        }

        // Add 14 roads with distance (km), toll (rupees), and traffic weight
        graph.addRoad(new Road(mumbai, pune, 149, 150, 1.2));
        graph.addRoad(new Road(pune, aurangabad, 210, 0, 0.8));
        graph.addRoad(new Road(aurangabad, nashik, 150, 100, 0.9));
        graph.addRoad(new Road(nashik, mumbai, 190, 0, 1.0));
        graph.addRoad(new Road(nashik, nagpur, 545, 0, 0.7));
        graph.addRoad(new Road(nagpur, indore, 580, 200, 0.6));
        graph.addRoad(new Road(indore, bhopal, 350, 150, 0.8));
        graph.addRoad(new Road(bhopal, nagpur, 380, 0, 0.9));
        graph.addRoad(new Road(mumbai, ahmedabad, 530, 200, 1.1));
        graph.addRoad(new Road(ahmedabad, indore, 380, 0, 0.7));
        graph.addRoad(new Road(pune, nashik, 220, 0, 1.0));
        graph.addRoad(new Road(indore, ahmedabad, 380, 100, 0.8));
        graph.addRoad(new Road(aurangabad, bhopal, 450, 100, 0.7));
        graph.addRoad(new Road(mumbai, nagpur, 1150, 400, 0.9));

        return graph;
    }
}
