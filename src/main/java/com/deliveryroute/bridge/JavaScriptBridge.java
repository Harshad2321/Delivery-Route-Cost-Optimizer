package com.deliveryroute.bridge;

import com.deliveryroute.model.Route;

import javafx.scene.web.WebEngine;

/**
 * Bridge for Java ↔ JavaScript communication
 * Handles drawing routes on Leaflet map, clearing routes, highlighting cities
 */
public class JavaScriptBridge {
    private final WebEngine webEngine;

    public JavaScriptBridge(WebEngine webEngine) {
        this.webEngine = webEngine;
    }

    /**
     * Draw a route on the map using Leaflet
     */
    public void drawRoute(Route route) {
        // Convert coordinates to valid JavaScript array format
        double[][] coords = route.getLatLngList();
        StringBuilder coordsJs = new StringBuilder("[");
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) coordsJs.append(",");
            coordsJs.append("[").append(coords[i][0]).append(",").append(coords[i][1]).append("]");
        }
        coordsJs.append("]");
        
        StringBuilder jsCode = new StringBuilder();
        jsCode.append("if (typeof drawRoute === 'function') {");
        jsCode.append("  var coords = ").append(coordsJs).append(";");
        jsCode.append("  var color = '").append(getColorForVehicle(route.getVehicleType())).append("';");
        jsCode.append("  var label = '").append(route.getFormattedPath()).append("';");
        jsCode.append("  drawRoute(coords, color, label);");
        jsCode.append("}");
        
        webEngine.executeScript(jsCode.toString());
    }

    /**
     * Clear all routes from the map
     */
    public void clearRoute() {
        webEngine.executeScript("if (typeof clearRoute === 'function') { clearRoute(); }");
    }

    /**
     * Highlight specific cities on the map
     */
    public void highlightCities(Route route) {
        StringBuilder jsCode = new StringBuilder();
        jsCode.append("if (typeof highlightCities === 'function') {");
        jsCode.append("  var cities = [");
        
        for (int i = 0; i < route.getCityPath().size(); i++) {
            if (i > 0) jsCode.append(",");
            jsCode.append("'").append(route.getCityPath().get(i).getName()).append("'");
        }
        
        jsCode.append("];");
        jsCode.append("  highlightCities(cities);");
        jsCode.append("}");
        
        webEngine.executeScript(jsCode.toString());
    }

    /**
     * Fit map view to contain all route cities
     */
    public void fitMapToCities(Route route) {
        webEngine.executeScript("if (typeof fitToCities === 'function') { fitToCities(); }");
    }

    /**
     * Force Leaflet to recalculate map container dimensions.
     */
    public void invalidateMapSize() {
        webEngine.executeScript("if (typeof ensureMapSize === 'function') { ensureMapSize(); }");
    }

    /**
     * Update or create delivery marker with ETA popup on the map.
     */
    public void updateDeliveryMarker(double lat, double lng, String eta) {
        String safeEta = eta == null ? "N/A" : eta.replace("'", "\\'");
        String js = String.format(
                "if (typeof updateDeliveryMarker === 'function') { updateDeliveryMarker(%f, %f, '%s'); }",
                lat,
                lng,
                safeEta
        );
        webEngine.executeScript(js);
    }

    /**
     * Get map color for vehicle type
     */
    private String getColorForVehicle(String vehicleType) {
        return switch (vehicleType) {
            case "Bike" -> "#22C55E";      // Green
            case "Van" -> "#3B82F6";       // Blue
            case "Truck" -> "#EF4444";     // Red
            default -> "#6B7280";          // Gray
        };
    }

    /**
     * Execute arbitrary JavaScript on the map
     */
    public Object executeScript(String script) {
        return webEngine.executeScript(script);
    }

    /**
     * Build the self-contained Leaflet HTML content for JavaFX WebView.
     */
    public static String buildMapHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="X-UA-Compatible" content="IE=edge">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Delivery Route Map</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
                <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body { margin:0; padding:0; font-family:'Segoe UI', sans-serif; }
                    #map { width:100%; height:100vh; }

                    #info-card {
                        position:absolute; top:16px; right:16px; z-index:1000;
                        background:white; border-radius:12px;
                        padding:14px 18px; min-width:220px;
                        box-shadow:0 2px 12px rgba(0,0,0,0.12);
                        border:0.5px solid rgba(0,0,0,0.08);
                        display:none;
                    }
                    #info-card h3 { font-size:14px; font-weight:600; margin:0 0 8px; color:#1A1A18; }
                    #info-card .row { display:flex; justify-content:space-between; font-size:13px; padding:3px 0; }
                    #info-card .label { color:#888880; }
                    #info-card .value { font-weight:500; color:#1A1A18; }
                    #info-card .total { border-top:0.5px solid #eee; margin-top:8px; padding-top:8px; font-weight:600; color:#E5581E; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <div id="info-card">
                    <h3 id="route-title">Route</h3>
                    <div class="row"><span class="label">Distance</span><span class="value" id="distance-value">-</span></div>
                    <div class="row"><span class="label">Fuel</span><span class="value" id="fuel-value">-</span></div>
                    <div class="row"><span class="label">Toll</span><span class="value" id="toll-value">-</span></div>
                    <div class="row"><span class="label">Traffic</span><span class="value" id="traffic-value">-</span></div>
                    <div class="row total"><span>Total</span><span id="total-value">-</span></div>
                </div>
                <script>
                    let map = null;
                    let routePolyline = null;
                    let routeMarkers = [];
                    let cityMarkers = [];

                    function ensureMapSize() {
                        if (map) {
                            map.invalidateSize(true);
                        }
                    }

                    function initMap() {
                        if (map !== null) return;
                        map = L.map('map', {
                            center: [20.5, 76.5],
                            zoom: 5,
                            zoomControl: true,
                            scrollWheelZoom: true
                        });

                        const osmLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            attribution: '© OpenStreetMap contributors',
                            subdomains: ['a','b','c'],
                            maxZoom: 18,
                            minZoom: 3
                        });

                        let fallbackApplied = false;
                        osmLayer.on('tileerror', function () {
                            if (!fallbackApplied) {
                                fallbackApplied = true;
                                map.removeLayer(osmLayer);
                                L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
                                    attribution: '© CartoDB'
                                }).addTo(map);
                            }
                        });
                        osmLayer.addTo(map);

                        addCityMarkers();
                        setTimeout(ensureMapSize, 100);
                    }

                    function addCityMarkers() {
                        const cities = [
                            {name: 'Mumbai', lat: 19.0760, lng: 72.8777},
                            {name: 'Pune', lat: 18.5204, lng: 73.8567},
                            {name: 'Aurangabad', lat: 19.8762, lng: 75.3433},
                            {name: 'Nashik', lat: 19.9975, lng: 73.7898},
                            {name: 'Nagpur', lat: 21.1458, lng: 79.0882},
                            {name: 'Ahmedabad', lat: 23.0225, lng: 72.5714},
                            {name: 'Indore', lat: 22.7196, lng: 75.8577},
                            {name: 'Bhopal', lat: 23.1815, lng: 79.9864}
                        ];

                        cities.forEach(city => {
                            let marker = L.circleMarker([city.lat, city.lng], {
                                radius: 10,
                                fillColor: '#FFA500',
                                color: '#D97E00',
                                weight: 2,
                                opacity: 1,
                                fillOpacity: 0.9
                            }).bindPopup('<b>' + city.name + '</b>').addTo(map);
                            cityMarkers.push(marker);
                        });
                    }

                    function drawRoute(coords, color, label) {
                        clearRoute();
                        if (!coords || coords.length === 0) {
                            return;
                        }

                        routePolyline = L.polyline(coords, {
                            color: color,
                            weight: 5,
                            opacity: 0.85,
                            dashArray: '10,5'
                        }).addTo(map);

                        coords.forEach((coord, idx) => {
                            const marker = L.circleMarker(coord, {
                                radius: 8,
                                fillColor: color,
                                color: '#FFFFFF',
                                weight: 2,
                                fillOpacity: 0.95
                            }).bindPopup('Stop ' + (idx + 1)).addTo(map);
                            routeMarkers.push(marker);
                        });

                        fitToCities();
                    }

                    function clearRoute() {
                        if (routePolyline && map.hasLayer(routePolyline)) {
                            map.removeLayer(routePolyline);
                        }
                        routePolyline = null;
                        routeMarkers.forEach(marker => {
                            if (map && map.hasLayer(marker)) {
                                map.removeLayer(marker);
                            }
                        });
                        routeMarkers = [];
                    }

                    function highlightCities(cityNames) {
                        cityMarkers.forEach(marker => marker.setStyle({fillColor: '#FFA500'}));
                    }

                    function fitToCities() {
                        if (map && routeMarkers.length > 0) {
                            let group = new L.featureGroup(routeMarkers);
                            let bounds = group.getBounds();
                            if (bounds.isValid()) {
                                map.fitBounds(bounds, {padding: [80, 80]});
                            }
                        }
                    }

                    function updateDeliveryMarker(lat, lng, eta) {
                        if (!map) {
                            return;
                        }
                        if (!map.deliveryMarker) {
                            map.deliveryMarker = L.marker([lat, lng]).addTo(map);
                            map.etaPopup = L.popup({closeButton: false, autoClose: false, closeOnClick: false});
                        }
                        map.deliveryMarker.setLatLng([lat, lng]);
                        if (map.etaPopup) {
                            map.etaPopup
                                .setLatLng([lat, lng])
                                .setContent('ETA: ' + eta)
                                .openOn(map);
                        }
                    }

                    function updateInfoCard(routeName, distanceKm, fuelCost, tollCost, trafficPenalty, totalCost) {
                        const card = document.getElementById('info-card');
                        document.getElementById('route-title').textContent = routeName;
                        document.getElementById('distance-value').textContent = distanceKm;
                        document.getElementById('fuel-value').textContent = fuelCost;
                        document.getElementById('toll-value').textContent = tollCost;
                        document.getElementById('traffic-value').textContent = trafficPenalty;
                        document.getElementById('total-value').textContent = totalCost;
                        card.style.display = 'block';
                    }

                    window.initMap = initMap;
                    document.addEventListener('DOMContentLoaded', initMap);
                    window.addEventListener('resize', ensureMapSize);
                    setTimeout(initMap, 300);
                </script>
            </body>
            </html>
            """;
    }

    public void updateRouteInfoCard(String routeName,
                                    double distanceKm,
                                    double fuelCost,
                                    double tollCost,
                                    double trafficPenalty,
                                    double totalCost) {
        String safeRouteName = routeName == null ? "Route" : routeName.replace("'", "\\'");
        String js = String.format(
                "if (typeof updateInfoCard === 'function') { updateInfoCard('%s', '%.2f km', 'INR %.2f', 'INR %.2f', 'INR %.2f', 'INR %.2f'); }",
                safeRouteName,
                distanceKm,
                fuelCost,
                tollCost,
                trafficPenalty,
                totalCost
        );
        webEngine.executeScript(js);
    }
}
