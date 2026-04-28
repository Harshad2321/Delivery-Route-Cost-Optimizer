package com.deliveryroute;

import com.deliveryroute.web.AuthWebServer;

/**
 * Web-only entry point for the Delivery Route Cost Optimizer application.
 * Runs the HTTP server on port 8081.
 */
public class WebAppMain {

    public static void main(String[] args) {
        int port = 8081;
        System.out.println("Starting Delivery Route Cost Optimizer Web Server...");
        AuthWebServer.start(port);
        System.out.println("\n✓ Web server started successfully!");
        System.out.println("✓ Access the application at: http://localhost:" + port);
        System.out.println("\nPress Ctrl+C to stop the server.\n");
        
        // Keep the main thread alive
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            AuthWebServer.stop();
            System.out.println("Server stopped.");
        }));
    }
}
