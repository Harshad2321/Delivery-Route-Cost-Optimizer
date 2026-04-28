-- Comprehensive database initialization for Delivery Route Optimizer

-- 1. Tracking updates table
CREATE TABLE IF NOT EXISTS tracking_updates (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL,
  delivery_user VARCHAR(100) NOT NULL,
  lat DOUBLE NOT NULL,
  lng DOUBLE NOT NULL,
  recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_order (order_id),
  FOREIGN KEY (order_id) REFERENCES delivery_orders(id) ON DELETE CASCADE
);

-- 2. Notifications table
CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  target_user VARCHAR(100) NOT NULL,
  message TEXT NOT NULL,
  is_read TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_unread (target_user, is_read)
);

-- 3. Delivery ratings table
CREATE TABLE IF NOT EXISTS delivery_ratings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL UNIQUE,
  customer_user VARCHAR(100) NOT NULL,
  delivery_user VARCHAR(100) NOT NULL,
  rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  review TEXT,
  rated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (order_id) REFERENCES delivery_orders(id)
);

-- 4. Admin vehicle configuration table
CREATE TABLE IF NOT EXISTS admin_vehicle_configs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  vehicle_type VARCHAR(50) NOT NULL UNIQUE,
  max_weight_kg DECIMAL(10,2) NOT NULL,
  max_distance_km DECIMAL(10,2) NOT NULL,
  price_per_km DECIMAL(10,2) NOT NULL,
  toll_charge DECIMAL(10,2) NOT NULL DEFAULT 0,
  driver_profit_percent DECIMAL(5,2) NOT NULL DEFAULT 10,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_vehicle_type (vehicle_type)
);

-- 5. Admin item types configuration table
CREATE TABLE IF NOT EXISTS admin_item_types (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_type_name VARCHAR(100) NOT NULL UNIQUE,
  allowed_vehicles VARCHAR(100) NOT NULL,
  base_price DECIMAL(10,2) NOT NULL,
  toll_threshold_km DECIMAL(10,2) DEFAULT 50,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_item_type_name (item_type_name)
);

-- 6. Admin pricing rules table
CREATE TABLE IF NOT EXISTS admin_pricing_rules (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_name VARCHAR(100) NOT NULL UNIQUE,
  base_price DECIMAL(10,2) NOT NULL,
  toll_threshold_km DECIMAL(10,2) DEFAULT 50,
  toll_amount DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_rule_name (rule_name)
);

-- 7. Seed admin vehicle configurations
INSERT IGNORE INTO admin_vehicle_configs (vehicle_type, max_weight_kg, max_distance_km, price_per_km, toll_charge, driver_profit_percent)
VALUES
  ('Bike', 5.0, 500.0, 2.0, 0, 15),
  ('Van', 50.0, 1000.0, 3.5, 50, 12),
  ('Truck', 500.0, 5000.0, 5.0, 100, 10);

-- 8. Seed admin item types
INSERT IGNORE INTO admin_item_types (item_type_name, allowed_vehicles, base_price, toll_threshold_km)
VALUES
  ('Documents', 'Bike,Van,Truck', 0, 50),
  ('Food', 'Bike,Van', 100, 30),
  ('Electronics', 'Van,Truck', 150, 100),
  ('Furniture', 'Truck', 500, 200);

-- 9. Seed admin pricing rules
INSERT IGNORE INTO admin_pricing_rules (rule_name, base_price, toll_threshold_km, toll_amount)
VALUES
  ('Standard Pricing', 0, 50, 35),
  ('Premium Pricing', 50, 30, 75);
