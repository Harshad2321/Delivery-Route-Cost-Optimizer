# Delivery Route Cost Optimizer

A production-ready JavaFX desktop application with interactive Leaflet map integration for optimizing delivery routes using Dijkstra's shortest path algorithm with multi-criteria cost calculation.

## Features

- **Interactive Map Visualization**: Real-time Leaflet.js map with OpenStreetMap tiles
- **Multi-Vehicle Support**: Bike, Van, and Truck with different fuel costs and capacities
- **Cost Strategy Selection**: Standard cost (includes tolls) or Toll-Free routing
- **Dijkstra's Algorithm**: Optimized route calculation with multi-criteria cost
- **Route History**: Persist and view all calculated routes
- **Cost Breakdown**: Detailed fuel, toll, and traffic penalty analysis
- **Java ↔ JavaScript Bridge**: Seamless communication between Java backend and web-based map

## System Requirements

- Java 17 or higher
- Maven 3.9.15 or higher
- MySQL 8.0.33 or higher
- Windows, macOS, or Linux

## Setup Instructions

### 1. Database Configuration

Create a MySQL database:
```bash
mysql -u root -p < database/schema.sql
```

### 2. Update Configuration

Edit `config.properties` with your database credentials:
```properties
db.url=jdbc:mysql://localhost:3306/delivery_route_optimizer
db.username=root
db.password=your_password
```

### 3. Build the Project

```bash
mvn clean compile
```

### 4. Run the Application

```bash
mvn javafx:run
```

## Architecture

### Model Layer
- **City**: Represents a city node with GPS coordinates
- **Road**: Bidirectional edge with distance, toll, and traffic weight
- **Vehicle**: Abstract base class (Bike, Van, Truck) with fuel rates and capacities
- **Route**: Immutable result object with complete cost breakdown

### Algorithm Layer
- **Graph**: Adjacency list with 8 Indian cities and 14 connected roads
- **DijkstraOptimizer**: Shortest path calculation with multi-criteria cost

### Cost Strategy Layer
- **CostStrategy**: Interface for different cost calculation methods
- **StandardCostStrategy**: Includes fuel + toll + traffic penalties
- **TollFreeCostStrategy**: Minimizes tolls by inflating traffic weight

### Database Layer
- **DatabaseConnection**: Singleton connection manager
- **RouteRepository**: CRUD operations for route history

### UI Layer
- **Main**: JavaFX Application entry point
- **MainController**: FXML controller with all UI logic
- **main.fxml**: FXML layout definition
- **JavaScriptBridge**: Java ↔ JavaScript communication for map interaction

### Web Layer
- **Leaflet.js Map**: Interactive map rendering via WebView
- **OpenStreetMap Tiles**: Free tile layer (no API key required)

## Cost Calculation Formula

### Standard Cost Strategy
```
Cost = (Distance × Vehicle FuelRate) + Toll + (Distance × TrafficWeight × 0.5)
```

### Toll-Free Strategy
```
Cost = (Distance × Vehicle FuelRate) + (Distance × TrafficWeight × 3.0)
```
Higher traffic multiplier discourages toll roads by inflating their cost.

## Cities and Routes

**Available Cities** (8 Indian metropolitan areas):
- Mumbai, Pune, Aurangabad, Nashik, Nagpur, Ahmedabad, Indore, Bhopal

**Road Network** (14 bidirectional roads with varying tolls and traffic):
- Routes are realistic representations of major highways
- Toll charges: ₹0–400 per road
- Traffic weights: 0.6–1.2

## Project Structure

```
delivery-route-optimizer/
├── pom.xml
├── config.properties
├── .gitignore
├── database/
│   └── schema.sql
├── src/main/
│   ├── java/com/deliveryroute/
│   │   ├── Main.java
│   │   ├── algorithm/
│   │   │   ├── Graph.java
│   │   │   └── DijkstraOptimizer.java
│   │   ├── bridge/
│   │   │   └── JavaScriptBridge.java
│   │   ├── cost/
│   │   │   ├── CostStrategy.java
│   │   │   ├── StandardCostStrategy.java
│   │   │   └── TollFreeCostStrategy.java
│   │   ├── database/
│   │   │   ├── DatabaseConnection.java
│   │   │   └── RouteRepository.java
│   │   ├── exception/
│   │   │   └── RouteNotFoundException.java
│   │   ├── model/
│   │   │   ├── City.java
│   │   │   ├── Road.java
│   │   │   ├── Vehicle.java
│   │   │   ├── Bike.java
│   │   │   ├── Van.java
│   │   │   ├── Truck.java
│   │   │   └── Route.java
│   │   └── ui/
│   │       └── MainController.java
│   └── resources/
│       ├── config.properties
│       └── main.fxml
└── target/
```

## Dependencies

- **JavaFX 21.0.1**: Modern UI framework
- **MySQL Connector Java 8.0.33**: Database JDBC driver
- **Jackson Databind 2.15.2**: JSON serialization
- **Leaflet.js 1.9.4**: Interactive map library (CDN)
- **OpenStreetMap**: Free tile layer

## Troubleshooting

### Database Connection Error
Ensure MySQL server is running and credentials in `config.properties` are correct.

### Map Not Loading
Check internet connection (required for OpenStreetMap tiles).

### No Route Found
Verify both source and destination cities are different and exist in the database.

## Future Enhancements

- Multi-leg route planning
- Real-time traffic API integration
- Route export to GPX format
- Delivery time estimation
- Driver-specific constraints
- Dynamic vehicle pricing

## License

This project is provided as-is for educational and commercial use.

## Author

Built with JavaFX, Dijkstra's algorithm, and Leaflet.js
