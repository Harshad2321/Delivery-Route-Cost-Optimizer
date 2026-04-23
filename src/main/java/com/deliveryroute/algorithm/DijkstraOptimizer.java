package com.deliveryroute.algorithm;

import com.deliveryroute.cost.CostStrategy;
import com.deliveryroute.model.City;
import com.deliveryroute.model.Road;
import com.deliveryroute.model.Route;
import com.deliveryroute.model.Vehicle;
import com.deliveryroute.exception.RouteNotFoundException;

import java.util.*;

/**
 * Dijkstra's shortest path algorithm with multi-criteria cost optimization.
 */
public class DijkstraOptimizer {
    private final Graph graph;
    private final CostStrategy costStrategy;

    public DijkstraOptimizer(Graph graph, CostStrategy costStrategy) {
        this.graph = graph;
        this.costStrategy = costStrategy;
    }

    /**
     * Find optimal route from source to destination using Dijkstra's algorithm
     */
    public Route findOptimalRoute(City sourceCity, City destinationCity, Vehicle vehicle) 
            throws RouteNotFoundException {
        
        Map<Integer, Double> distances = new HashMap<>();
        Map<Integer, City> previous = new HashMap<>();
        Map<Integer, Double> fuelCosts = new HashMap<>();
        Map<Integer, Double> tollCosts = new HashMap<>();
        Map<Integer, Double> trafficCosts = new HashMap<>();
        Map<Integer, Double> segmentDistances = new HashMap<>();
        
        PriorityQueue<CityDistance> minHeap = new PriorityQueue<>();

        // Initialize distances
        for (City city : graph.getCities()) {
            distances.put(city.getId(), Double.MAX_VALUE);
            fuelCosts.put(city.getId(), 0.0);
            tollCosts.put(city.getId(), 0.0);
            trafficCosts.put(city.getId(), 0.0);
            segmentDistances.put(city.getId(), 0.0);
        }

        distances.put(sourceCity.getId(), 0.0);
        minHeap.offer(new CityDistance(sourceCity, 0.0));

        while (!minHeap.isEmpty()) {
            CityDistance current = minHeap.poll();
            City currentCity = current.city;
            double currentDistance = current.distance;

            if (currentDistance > distances.get(currentCity.getId())) {
                continue;
            }

            if (currentCity.getId() == destinationCity.getId()) {
                break;
            }

            // Explore neighbors
            for (Road road : graph.getRoadsFrom(currentCity)) {
                City neighbor = road.getDestination();
                double edgeCost = costStrategy.calculateEdgeCost(road, vehicle);
                double newTotalCost = distances.get(currentCity.getId()) + edgeCost;

                if (newTotalCost < distances.get(neighbor.getId())) {
                    distances.put(neighbor.getId(), newTotalCost);
                    previous.put(neighbor.getId(), currentCity);
                    
                    // Track cost components
                    fuelCosts.put(neighbor.getId(), 
                        fuelCosts.get(currentCity.getId()) + 
                        road.getDistanceKm() * vehicle.getFuelRateRupeesPerKm());
                    
                    tollCosts.put(neighbor.getId(), 
                        tollCosts.get(currentCity.getId()) + road.getTollChargeRupees());
                    
                    trafficCosts.put(neighbor.getId(), 
                        trafficCosts.get(currentCity.getId()) + 
                        road.getDistanceKm() * road.getTrafficWeight() * 0.5);
                    
                    segmentDistances.put(neighbor.getId(), 
                        segmentDistances.get(currentCity.getId()) + road.getDistanceKm());

                    minHeap.offer(new CityDistance(neighbor, newTotalCost));
                }
            }
        }

        // Check if route exists
        if (distances.get(destinationCity.getId()) == Double.MAX_VALUE) {
            throw new RouteNotFoundException("No route found from " + sourceCity.getName() + 
                    " to " + destinationCity.getName());
        }

        // Reconstruct path
        List<City> path = new ArrayList<>();
        City current = destinationCity;
        while (current != null) {
            path.add(0, current);
            current = previous.get(current.getId());
        }

        // Create route object with cost breakdown
        double totalCost = distances.get(destinationCity.getId());
        double totalFuelCost = fuelCosts.get(destinationCity.getId());
        double totalTollCost = tollCosts.get(destinationCity.getId());
        double totalTrafficPenalty = trafficCosts.get(destinationCity.getId());
        double totalDistanceKm = segmentDistances.get(destinationCity.getId());

        return new Route(path, vehicle.getVehicleType(), costStrategy.getStrategyName(), 
                totalCost, totalFuelCost, totalTollCost, totalTrafficPenalty, totalDistanceKm);
    }

    /**
     * Inner class for priority queue ordering
     */
    private static class CityDistance implements Comparable<CityDistance> {
        City city;
        double distance;

        CityDistance(City city, double distance) {
            this.city = city;
            this.distance = distance;
        }

        @Override
        public int compareTo(CityDistance other) {
            return Double.compare(this.distance, other.distance);
        }
    }
}
