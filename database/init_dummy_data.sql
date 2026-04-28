-- Direct SQL execution to initialize dummy data
-- Run this manually if dummy data doesn't load automatically

USE delivery_route_optimizer;

-- Insert dummy orders (if not already present)
INSERT IGNORE INTO delivery_orders (customer_email, source_city, destination_city, vehicle_type, cost_strategy, total_cost, status, weight_kg, pickup_slot) VALUES
  ('customer1@drc.local', 'Mumbai', 'Pune', 'Bike', 'STANDARD', 150.00, 'CREATED', 2.5, '09:00 - 11:00'),
  ('customer2@drc.local', 'Mumbai', 'Aurangabad', 'Van', 'STANDARD', 350.00, 'PLACED', 5.0, '10:00 - 12:00'),
  ('customer3@drc.local', 'Pune', 'Nashik', 'Truck', 'STANDARD', 500.00, 'ACCEPTED', 15.0, '14:00 - 16:00'),
  ('customer4@drc.local', 'Nagpur', 'Ahmedabad', 'Bike', 'STANDARD', 200.00, 'PICKED', 1.5, '08:00 - 10:00'),
  ('customer5@drc.local', 'Indore', 'Bhopal', 'Van', 'STANDARD', 280.00, 'IN_TRANSIT', 8.0, '11:00 - 13:00'),
  ('customer6@drc.local', 'Ahmedabad', 'Mumbai', 'Truck', 'STANDARD', 600.00, 'DELIVERED', 20.0, '07:00 - 09:00'),
  ('customer7@drc.local', 'Nashik', 'Pune', 'Bike', 'STANDARD', 120.00, 'CANCELLED', 1.0, '15:00 - 17:00'),
  ('customer8@drc.local', 'Mumbai', 'Indore', 'Van', 'STANDARD', 400.00, 'CREATED', 4.5, '13:00 - 15:00');

-- Insert dummy riders  (if not already present)
INSERT IGNORE INTO riders (name, phone_number, vehicle_type, current_city, availability) VALUES
  ('Rajesh Kumar', '9876543210', 'Bike', 'Mumbai', 'AVAILABLE'),
  ('Priya Singh', '9876543211', 'Van', 'Mumbai', 'AVAILABLE'),
  ('Amit Patel', '9876543212', 'Truck', 'Pune', 'AVAILABLE'),
  ('Neha Verma', '9876543213', 'Bike', 'Aurangabad', 'BUSY'),
  ('Vikram Reddy', '9876543214', 'Van', 'Nashik', 'AVAILABLE'),
  ('Shalini Gupta', '9876543215', 'Bike', 'Nagpur', 'OFF_DUTY'),
  ('Arjun Desai', '9876543216', 'Truck', 'Ahmedabad', 'AVAILABLE'),
  ('Deepika Malhotra', '9876543217', 'Van', 'Indore', 'AVAILABLE');

-- Verify data was inserted
SELECT 'Orders' AS Entity, COUNT(*) AS Count FROM delivery_orders
UNION ALL
SELECT 'Riders' AS Entity, COUNT(*) AS Count FROM riders;
