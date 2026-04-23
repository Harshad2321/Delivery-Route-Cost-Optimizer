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
    @FXML private TextField emailField;
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
        String email = emailField.getText() == null ? "" : emailField.getText().trim().toLowerCase();
        String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

        if (email.isBlank() || password.isBlank()) {
            showError("Email and password are required");
            return;
        }

        if (!isValidEmail(email)) {
            showError("Enter a valid email address");
            return;
        }

        String accountType = getSelectedAccountType();
        if (!userRepository.authenticate(email, password, accountType)) {
            showError("Invalid credentials for selected role");
            return;
        }

        try {
            routeToRoleDashboard(accountType, email);
        } catch (IOException e) {
            showAlert("Unable to load dashboard: " + e.getMessage());
        }
    }

    @FXML
    private void onCreateAccount() {
        showAlert("Create customer and delivery partner accounts from the customer sign-up screen. Admin accounts are preset only.");
    }

    private String getSelectedAccountType() {
        if (adminRoleButton.isSelected()) {
            return "ADMIN";
        }
        if (deliveryRoleButton.isSelected()) {
            return "DELIVERY_PARTNER";
        }
        return "CUSTOMER";
    }

    private void routeToRoleDashboard(String accountType, String email) throws IOException {
        Stage stage = (Stage) emailField.getScene().getWindow();

        if ("ADMIN".equals(accountType)) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/admin.fxml"));
            Parent root = loader.load();
            AdminController controller = loader.getController();
            controller.setAdminUser(email);
            Scene scene = new Scene(root, 1400, 860);
            scene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Delivery Route Cost Optimizer - Admin Dashboard");
            return;
        }

        if ("DELIVERY_PARTNER".equals(accountType)) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/delivery.fxml"));
            Parent root = loader.load();
            DeliveryController controller = loader.getController();
            controller.setDeliveryUser(email);
            Scene scene = new Scene(root, 1400, 860);
            scene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Delivery Route Cost Optimizer - Delivery Console");
            return;
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.initializeCustomerSession(email);
        Scene scene = new Scene(root, 1400, 860);
        scene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Delivery Route Cost Optimizer - Customer Console");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
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
