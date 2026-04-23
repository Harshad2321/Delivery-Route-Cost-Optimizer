package com.deliveryroute;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main entry point for the Delivery Route Cost Optimizer application
 */
public class Main extends Application {
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/login.fxml"));
        Parent root = loader.load();

        // Create scene
        Scene scene = new Scene(root, 1400, 860);
        scene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());

        // Setup window
        primaryStage.setTitle("Delivery Route Cost Optimizer");
        primaryStage.setScene(scene);
        primaryStage.setWidth(1400);
        primaryStage.setHeight(860);
        primaryStage.setOnCloseRequest(e -> System.exit(0));

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
