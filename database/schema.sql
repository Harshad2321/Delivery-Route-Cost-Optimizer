CREATE DATABASE IF NOT EXISTS delivery_route_optimizer;
USE delivery_route_optimizer;

CREATE TABLE IF NOT EXISTS saved_routes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    source_city VARCHAR(100) NOT NULL,
    destination_city VARCHAR(100) NOT NULL,
    vehicle_type VARCHAR(50) NOT NULL,
    cost_strategy VARCHAR(50) NOT NULL,
    path_cities TEXT NOT NULL,
    total_cost DECIMAL(10,2) NOT NULL,
    fuel_cost DECIMAL(10,2) NOT NULL,
    toll_cost DECIMAL(10,2) NOT NULL,
    traffic_penalty DECIMAL(10,2) NOT NULL,
    total_distance_km DECIMAL(10,2) NOT NULL,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_source_dest ON saved_routes(source_city, destination_city);
CREATE INDEX idx_vehicle_type ON saved_routes(vehicle_type);
CREATE INDEX idx_calculated_at ON saved_routes(calculated_at DESC);
