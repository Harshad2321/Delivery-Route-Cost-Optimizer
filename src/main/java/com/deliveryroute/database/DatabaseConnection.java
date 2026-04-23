package com.deliveryroute.database;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton database connection manager
 * Reads credentials from config.properties at runtime
 */
public class DatabaseConnection {
    private static DatabaseConnection instance;
    private Connection connection;
    private final String url;
    private final String username;
    private final String password;
    private boolean available;

    private DatabaseConnection() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("config.properties not found");
            }
            props.load(input);
            this.url = props.getProperty("db.url");
            this.username = props.getProperty("db.username");
            this.password = props.getProperty("db.password");
            this.available = false;
            
            // Try initial connection
            try {
                this.connection = DriverManager.getConnection(url, username, password);
                this.available = true;
            } catch (SQLException e) {
                System.err.println("Warning: Database connection unavailable: " + e.getMessage());
                this.available = false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load database configuration: " + e.getMessage(), e);
        }
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(url, username, password);
            available = true;
        }
        return connection;
    }

    public boolean isAvailable() {
        return available;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                available = false;
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}
