package com.deliveryroute.ui;

import java.io.IOException;

import com.deliveryroute.database.UserRepository;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;

/**
 * Dedicated login controller with role-based routing.
 */
public class LoginController {
    @FXML private ToggleGroup roleToggleGroup;
    @FXML private ToggleButton customerRoleButton;
    @FXML private ToggleButton adminRoleButton;
    @FXML private ToggleButton deliveryRoleButton;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final UserRepository userRepository = new UserRepository();

    @FXML
    public void initialize() {
        customerRoleButton.setToggleGroup(roleToggleGroup);
        adminRoleButton.setToggleGroup(roleToggleGroup);
        deliveryRoleButton.setToggleGroup(roleToggleGroup);
        customerRoleButton.setSelected(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    @FXML
    private void onSignIn() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

        if (username.isBlank() || password.isBlank()) {
            showError("Username and password are required");
            return;
        }

        String role = getSelectedRole();
        if (!userRepository.authenticate(username, password, role)) {
            showError("Invalid credentials for selected role");
            return;
        }

        try {
            routeToRoleDashboard(role, username);
        } catch (IOException e) {
            showAlert("Unable to load dashboard: " + e.getMessage());
        }
    }

    @FXML
    private void onCreateAccount() {
        showAlert("Use existing seeded users or sign up from customer screen in main module.");
    }

    private String getSelectedRole() {
        if (adminRoleButton.isSelected()) {
            return "ADMIN";
        }
        if (deliveryRoleButton.isSelected()) {
            return "DELIVERY";
        }
        return "CUSTOMER";
    }

    private void routeToRoleDashboard(String role, String username) throws IOException {
        Stage stage = (Stage) usernameField.getScene().getWindow();

        if ("ADMIN".equals(role)) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/admin.fxml"));
            Parent root = loader.load();
            AdminController controller = loader.getController();
            controller.setAdminUser(username);
            Scene scene = new Scene(root, 1400, 860);
            scene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Delivery Route Cost Optimizer - Admin Dashboard");
            return;
        }

        if ("DELIVERY".equals(role)) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/delivery.fxml"));
            Parent root = loader.load();
            DeliveryController controller = loader.getController();
            controller.setDeliveryUser(username);
            Scene scene = new Scene(root, 1400, 860);
            scene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Delivery Route Cost Optimizer - Delivery Console");
            return;
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.initializeCustomerSession(username);
        Scene scene = new Scene(root, 1400, 860);
        scene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Delivery Route Cost Optimizer - Customer Console");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
