CREATE DATABASE IF NOT EXISTS delivery_route_optimizer;
USE delivery_route_optimizer;

CREATE TABLE IF NOT EXISTS app_users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active TINYINT(1) DEFAULT 1,
    current_lat DOUBLE DEFAULT NULL,
    current_lng DOUBLE DEFAULT NULL,
    last_seen_at DATETIME DEFAULT NULL,
    CONSTRAINT chk_account_type CHECK (account_type IN ('CUSTOMER', 'DELIVERY_PARTNER', 'ADMIN'))
);

INSERT IGNORE INTO app_users (full_name, account_type, email, password)
VALUES
    ('Admin One', 'ADMIN', 'admin1@drc.local', 'Admin@123'),
    ('Admin Two', 'ADMIN', 'admin2@drc.local', 'Admin@234'),
    ('Admin Three', 'ADMIN', 'admin3@drc.local', 'Admin@345'),
    ('Admin Four', 'ADMIN', 'admin4@drc.local', 'Admin@456');

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
