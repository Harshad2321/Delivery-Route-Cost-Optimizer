-- Insert 10 random orders for testing
INSERT INTO delivery_orders (customer_email, source_city, destination_city, vehicle_type, cost_strategy, total_cost, status, weight_kg, pickup_slot) VALUES
('order1@test.com', 'Mumbai', 'Pune', 'Bike', 'STANDARD', 500, 'CREATED', 2.5, 'Morning'),
('order2@test.com', 'Bangalore', 'Hyderabad', 'Van', 'STANDARD', 800, 'CREATED', 15.0, 'Afternoon'),
('order3@test.com', 'Delhi', 'Jaipur', 'Truck', 'STANDARD', 1200, 'CREATED', 45.0, 'Morning'),
('order4@test.com', 'Chennai', 'Bangalore', 'Bike', 'STANDARD', 600, 'PLACED', 3.0, 'Evening'),
('order5@test.com', 'Kolkata', 'Pune', 'Van', 'STANDARD', 900, 'ACCEPTED', 20.0, 'Morning'),
('order6@test.com', 'Ahmedabad', 'Delhi', 'Truck', 'STANDARD', 1500, 'PICKED', 50.0, 'Afternoon'),
('order7@test.com', 'Pune', 'Mumbai', 'Bike', 'STANDARD', 450, 'IN_TRANSIT', 1.5, 'Evening'),
('order8@test.com', 'Hyderabad', 'Bangalore', 'Van', 'STANDARD', 700, 'DELIVERED', 18.0, 'Morning'),
('order9@test.com', 'Jaipur', 'Delhi', 'Truck', 'STANDARD', 1100, 'CREATED', 40.0, 'Afternoon'),
('order10@test.com', 'Surat', 'Ahmedabad', 'Bike', 'STANDARD', 350, 'CREATED', 1.0, 'Evening');
