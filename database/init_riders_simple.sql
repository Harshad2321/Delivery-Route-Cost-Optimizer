-- Simple riders table creation without complex IF NOT EXISTS
CREATE TABLE IF NOT EXISTS riders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    vehicle_type VARCHAR(50) NOT NULL,
    availability VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    current_city VARCHAR(100) NOT NULL,
    current_lat DOUBLE DEFAULT 0,
    current_lng DOUBLE DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_vehicle_type CHECK (vehicle_type IN ('Bike', 'Van', 'Truck')),
    CONSTRAINT chk_availability CHECK (availability IN ('AVAILABLE', 'BUSY', 'OFF_DUTY')),
    INDEX idx_availability (availability),
    INDEX idx_vehicle_type (vehicle_type),
    INDEX idx_current_city (current_city)
);

-- Seed dummy riders
INSERT IGNORE INTO riders (name, phone_number, vehicle_type, current_city, availability)
VALUES
  ('Rajesh Kumar', '9876543210', 'Bike', 'Mumbai', 'AVAILABLE'),
  ('Priya Singh', '9876543211', 'Van', 'Mumbai', 'AVAILABLE'),
  ('Amit Patel', '9876543212', 'Truck', 'Pune', 'AVAILABLE'),
  ('Neha Verma', '9876543213', 'Bike', 'Aurangabad', 'BUSY'),
  ('Vikram Reddy', '9876543214', 'Van', 'Nashik', 'AVAILABLE'),
  ('Shalini Gupta', '9876543215', 'Bike', 'Nagpur', 'OFF_DUTY'),
  ('Arjun Desai', '9876543216', 'Truck', 'Ahmedabad', 'AVAILABLE'),
  ('Deepika Malhotra', '9876543217', 'Van', 'Indore', 'AVAILABLE');

-- Add columns to delivery_orders if they don't already exist
ALTER TABLE delivery_orders ADD assigned_rider_id BIGINT NULL;
ALTER TABLE delivery_orders ADD weight_kg DECIMAL(10,2) DEFAULT 0;
ALTER TABLE delivery_orders ADD pickup_slot VARCHAR(30);
ALTER TABLE delivery_orders ADD picked_at TIMESTAMP NULL;
ALTER TABLE delivery_orders ADD in_transit_at TIMESTAMP NULL;
ALTER TABLE delivery_orders ADD customer_email VARCHAR(191);

-- Add foreign key
ALTER TABLE delivery_orders ADD CONSTRAINT fk_delivery_orders_rider FOREIGN KEY (assigned_rider_id) REFERENCES riders(id) ON DELETE SET NULL;

-- Seed dummy orders
INSERT IGNORE INTO delivery_orders (customer_email, source_city, destination_city, vehicle_type, cost_strategy, total_cost, status, weight_kg, pickup_slot, assigned_rider_id)
VALUES
  ('customer1@test.com', 'Mumbai', 'Pune', 'Bike', 'STANDARD', 150.00, 'CREATED', 2.5, '09:00 - 11:00', 1),
  ('customer2@test.com', 'Mumbai', 'Aurangabad', 'Van', 'STANDARD', 350.00, 'PLACED', 5.0, '10:00 - 12:00', NULL),
  ('customer3@test.com', 'Pune', 'Nashik', 'Truck', 'STANDARD', 500.00, 'ACCEPTED', 15.0, '14:00 - 16:00', 3),
  ('customer4@test.com', 'Nagpur', 'Ahmedabad', 'Bike', 'STANDARD', 200.00, 'PICKED', 1.5, '08:00 - 10:00', 1),
  ('customer5@test.com', 'Indore', 'Bhopal', 'Van', 'STANDARD', 280.00, 'IN_TRANSIT', 8.0, '11:00 - 13:00', 2),
  ('customer6@test.com', 'Ahmedabad', 'Mumbai', 'Truck', 'STANDARD', 600.00, 'DELIVERED', 20.0, '07:00 - 09:00', 7),
  ('customer7@test.com', 'Nashik', 'Pune', 'Bike', 'STANDARD', 120.00, 'CANCELLED', 1.0, '15:00 - 17:00', NULL),
  ('customer8@test.com', 'Mumbai', 'Indore', 'Van', 'STANDARD', 400.00, 'CREATED', 4.5, '13:00 - 15:00', NULL);
