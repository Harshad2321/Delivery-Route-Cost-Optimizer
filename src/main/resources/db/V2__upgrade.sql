-- Delivery Route Cost Optimizer schema upgrade V2

-- 1. Real-time location for delivery users
ALTER TABLE app_users
  ADD COLUMN current_lat DOUBLE DEFAULT NULL,
  ADD COLUMN current_lng DOUBLE DEFAULT NULL,
  ADD COLUMN last_seen_at DATETIME DEFAULT NULL,
  ADD COLUMN is_active TINYINT(1) DEFAULT 1;

-- 2. Location ping history for tracking
CREATE TABLE IF NOT EXISTS tracking_updates (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  delivery_user VARCHAR(100) NOT NULL,
  lat DOUBLE NOT NULL,
  lng DOUBLE NOT NULL,
  recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_order (order_id),
  FOREIGN KEY (order_id) REFERENCES delivery_orders(id) ON DELETE CASCADE
);

-- 3. In-app notifications
CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  target_user VARCHAR(100) NOT NULL,
  message TEXT NOT NULL,
  is_read TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_unread (target_user, is_read)
);

-- 4. Delivery ratings
CREATE TABLE IF NOT EXISTS delivery_ratings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL UNIQUE,
  customer_user VARCHAR(100) NOT NULL,
  delivery_user VARCHAR(100) NOT NULL,
  rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  review TEXT,
  rated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (order_id) REFERENCES delivery_orders(id)
);

-- 5. Add ETA and completion time to orders
ALTER TABLE delivery_orders
  ADD COLUMN estimated_minutes INT DEFAULT NULL,
  ADD COLUMN actual_delivery DATETIME DEFAULT NULL;