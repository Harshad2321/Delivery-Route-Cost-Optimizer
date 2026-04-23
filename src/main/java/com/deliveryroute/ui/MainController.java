package com.deliveryroute.ui;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.deliveryroute.algorithm.DijkstraOptimizer;
import com.deliveryroute.algorithm.Graph;
import com.deliveryroute.bridge.JavaScriptBridge;
import com.deliveryroute.cost.StandardCostStrategy;
import com.deliveryroute.cost.TollFreeCostStrategy;
import com.deliveryroute.database.OrderRepository;
import com.deliveryroute.database.NotificationRepository;
import com.deliveryroute.database.RatingRepository;
import com.deliveryroute.database.RouteRepository;
import com.deliveryroute.database.TrackingRepository;
import com.deliveryroute.database.UserRepository;
import com.deliveryroute.exception.RouteNotFoundException;
import com.deliveryroute.model.Bike;
import com.deliveryroute.model.City;
import com.deliveryroute.model.Route;
import com.deliveryroute.model.Truck;
import com.deliveryroute.model.Van;
import com.deliveryroute.model.Vehicle;
import com.deliveryroute.service.NotificationService;
import com.deliveryroute.service.TrackingService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Main application controller handling UI logic
 */
public class MainController {

    private enum UserRole {
        CUSTOMER,
        ADMIN,
        DELIVERY
    }

    private record LoginSession(UserRole role, String username) {}
    
    @FXML private VBox homePanel;
    @FXML private VBox appPanel;
    @FXML private VBox homeCenterPane;
    @FXML private Label bellLabel;
    @FXML private Label unreadBadgeLabel;
    @FXML private Button loginButton;
    @FXML private Button signupButton;
    @FXML private Label homeStatusLabel;
    @FXML private WebView mapWebView;
    @FXML private ComboBox<String> sourceCombo;
    @FXML private ComboBox<String> destinationCombo;
    @FXML private ToggleGroup vehicleGroup;
    @FXML private RadioButton bikeRadio;
    @FXML private RadioButton vanRadio;
    @FXML private RadioButton truckRadio;
    @FXML private ComboBox<String> strategyCombo;
    @FXML private Button findRouteButton;
    @FXML private TextArea costSummaryText;
    @FXML private Button saveRouteButton;
    @FXML private Button viewHistoryButton;
    @FXML private Button placeOrderButton;
    @FXML private Button viewOrdersButton;
    @FXML private Button acceptOrderButton;
    @FXML private Button completeOrderButton;
    @FXML private Button simulateMovementButton;
    @FXML private TextArea orderSummaryText;
    @FXML private Label trackingStatusLabel;
    @FXML private Label loggedInUserLabel;
    @FXML private Label statusLabel;

    private Graph graph;
    private JavaScriptBridge jsbridge;
    private RouteRepository routeRepository;
    private OrderRepository orderRepository;
    private NotificationRepository notificationRepository;
    private NotificationService notificationService;
    private RatingRepository ratingRepository;
    private TrackingRepository trackingRepository;
    private UserRepository userRepository;
    private TrackingService trackingService;
    private Route currentRoute;
    private boolean mapInitialized;
    private boolean mapInitializationPending;
    private boolean mapReady;
    private UserRole currentUserRole;
    private String currentUsername;
    private OrderRepository.OrderRecord activeDeliveryOrder;
    private String selectedSourceCity;
    private String selectedDestinationCity;
    private ScheduledExecutorService customerTrackingScheduler;
    private ScheduledExecutorService notificationPollingScheduler;
    private volatile boolean ratingDialogOpen;

    @FXML
    public void initialize() {
        // Initialize graph with demo data
        graph = Graph.buildDemoGraph();
        routeRepository = new RouteRepository();
        orderRepository = new OrderRepository();
        notificationRepository = new NotificationRepository();
        notificationService = new NotificationService(notificationRepository);
        ratingRepository = new RatingRepository();
        trackingRepository = new TrackingRepository();
        userRepository = new UserRepository();
        trackingService = new TrackingService(graph, orderRepository, trackingRepository, userRepository);
        mapInitialized = false;
        mapInitializationPending = false;
        mapReady = false;

        // Populate city dropdowns
        ObservableList<String> cityNames = FXCollections.observableArrayList();
        for (City city : graph.getCities()) {
            cityNames.add(city.getName());
        }
        sourceCombo.setItems(cityNames);
        destinationCombo.setItems(cityNames);

        // Populate cost strategy combo
        ObservableList<String> strategies = FXCollections.observableArrayList(
            "Standard Cost",
            "Toll-Free Route"
        );
        strategyCombo.setItems(strategies);
        strategyCombo.setValue("Standard Cost");

        // Setup vehicle selection
        bikeRadio.setUserData("Bike");
        vanRadio.setUserData("Van");
        truckRadio.setUserData("Truck");
        vehicleGroup = new ToggleGroup();
        bikeRadio.setToggleGroup(vehicleGroup);
        vanRadio.setToggleGroup(vehicleGroup);
        truckRadio.setToggleGroup(vehicleGroup);
        bikeRadio.setSelected(true);

        // Start on home screen
        appPanel.setVisible(false);
        appPanel.setManaged(false);
        mapWebView.setVisible(false);
        mapWebView.setManaged(false);
        homeCenterPane.setVisible(true);
        homeCenterPane.setManaged(true);

        // Disable role actions until login + map ready
        findRouteButton.setDisable(true);
        saveRouteButton.setDisable(true);
        placeOrderButton.setDisable(true);
        viewOrdersButton.setDisable(true);
        acceptOrderButton.setDisable(true);
        completeOrderButton.setDisable(true);
        simulateMovementButton.setDisable(true);
        statusLabel.setText("Please login to continue");
        homeStatusLabel.setText("Choose Login or Sign Up");
        orderSummaryText.setText("Sign in to access role actions.");
        trackingStatusLabel.setText("Tracking inactive");
        unreadBadgeLabel.setText("0");

        // Add event listeners
        loginButton.setOnAction(e -> onLoginClicked());
        signupButton.setOnAction(e -> onSignupClicked());
        findRouteButton.setOnAction(e -> findRoute());
        saveRouteButton.setOnAction(e -> saveCurrentRoute());
        viewHistoryButton.setOnAction(e -> showRouteHistory());
        placeOrderButton.setOnAction(e -> placeOrderFromCurrentRoute());
        viewOrdersButton.setOnAction(e -> showOrdersForRole());
        acceptOrderButton.setOnAction(e -> acceptNextOrder());
        completeOrderButton.setOnAction(e -> completeActiveOrder());
        simulateMovementButton.setOnAction(e -> simulateDeliveryStep());
        bellLabel.setOnMouseClicked(e -> openNotifications());

        Platform.runLater(() -> {
            Stage stage = (Stage) homePanel.getScene().getWindow();
            if (stage != null) {
                stage.setOnCloseRequest(event -> {
                    stopCustomerTrackingPolling();
                    stopNotificationPolling();
                    trackingService.shutdown();
                });
            }
        });
    }

    private void onLoginClicked() {
        LoginSession session = showLoginDialog();
        if (session == null) {
            homeStatusLabel.setText("Login cancelled");
            return;
        }

        startUserSession(session);
    }

    private void onSignupClicked() {
        LoginSession session = showSignUpDialog();
        if (session == null) {
            homeStatusLabel.setText("Sign up cancelled");
            return;
        }

        startUserSession(session);
    }

    public void initializeCustomerSession(String username) {
        startUserSession(new LoginSession(UserRole.CUSTOMER, username));
    }

    private void startUserSession(LoginSession session) {
        currentUserRole = session.role();
        currentUsername = session.username();

        if (currentUserRole == UserRole.ADMIN) {
            openAdminDashboard(currentUsername);
            return;
        }
        if (currentUserRole == UserRole.DELIVERY) {
            openDeliveryDashboard(currentUsername);
            return;
        }

        homePanel.setVisible(false);
        homePanel.setManaged(false);
        homeCenterPane.setVisible(false);
        homeCenterPane.setManaged(false);
        appPanel.setVisible(true);
        appPanel.setManaged(true);
        mapWebView.setVisible(true);
        mapWebView.setManaged(true);

        loggedInUserLabel.setText("Logged in: " + currentUsername + " (" + currentUserRole + ")");

        initializeMapIfNeeded();
        applyRolePermissions();
        refreshOrderSummary();
        configureTrackingPollingForRole();
        startNotificationPolling();
        statusLabel.setText("Welcome " + currentUsername + "!");
    }

    private void configureTrackingPollingForRole() {
        stopCustomerTrackingPolling();
        if (currentUserRole == UserRole.CUSTOMER) {
            customerTrackingScheduler = Executors.newSingleThreadScheduledExecutor();
            customerTrackingScheduler.scheduleAtFixedRate(
                    () -> Platform.runLater(this::refreshCustomerTrackingLabel),
                    0,
                    5,
                    TimeUnit.SECONDS
            );
        }
    }

    private void stopCustomerTrackingPolling() {
        if (customerTrackingScheduler != null && !customerTrackingScheduler.isShutdown()) {
            customerTrackingScheduler.shutdownNow();
            customerTrackingScheduler = null;
        }
    }

    private void startNotificationPolling() {
        stopNotificationPolling();
        notificationPollingScheduler = Executors.newSingleThreadScheduledExecutor();
        notificationPollingScheduler.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            if (currentUsername == null) {
                return;
            }
            unreadBadgeLabel.setText(String.valueOf(notificationService.countUnread(currentUsername)));
            checkAndPromptPendingRatings();
        }), 0, 10, TimeUnit.SECONDS);
    }

    private void stopNotificationPolling() {
        if (notificationPollingScheduler != null && !notificationPollingScheduler.isShutdown()) {
            notificationPollingScheduler.shutdownNow();
            notificationPollingScheduler = null;
        }
    }

    private void openAdminDashboard(String adminUsername) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/admin.fxml"));
            Parent adminRoot = loader.load();
            AdminController adminController = loader.getController();
            adminController.setAdminUser(adminUsername);

            Stage adminStage = new Stage();
            adminStage.setTitle("Delivery Route Cost Optimizer - Admin Dashboard");
            Scene adminScene = new Scene(adminRoot, 1400, 860);
            adminScene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());
            adminStage.setScene(adminScene);
            adminStage.show();

            Stage currentStage = (Stage) homePanel.getScene().getWindow();
            currentStage.close();
        } catch (IOException e) {
            showError("Unable to open admin dashboard: " + e.getMessage());
        }
    }

    private void openDeliveryDashboard(String deliveryUsername) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/delivery.fxml"));
            Parent deliveryRoot = loader.load();
            DeliveryController deliveryController = loader.getController();
            deliveryController.setDeliveryUser(deliveryUsername);

            Stage deliveryStage = new Stage();
            deliveryStage.setTitle("Delivery Route Cost Optimizer - Delivery Console");
            Scene deliveryScene = new Scene(deliveryRoot, 1400, 860);
            deliveryScene.getStylesheets().add(getClass().getResource("/styles/theme.css").toExternalForm());
            deliveryStage.setScene(deliveryScene);
            deliveryStage.show();

            Stage currentStage = (Stage) homePanel.getScene().getWindow();
            currentStage.close();
        } catch (IOException e) {
            showError("Unable to open delivery console: " + e.getMessage());
        }
    }

    private void initializeMapIfNeeded() {
        if (mapInitialized || mapInitializationPending) {
            return;
        }

        mapInitializationPending = true;
        Platform.runLater(() -> {
            Stage stage = (Stage) mapWebView.getScene().getWindow();
            if (stage == null) {
                mapInitializationPending = false;
                return;
            }

            if (stage.isShowing()) {
                initializeMap();
                return;
            }

            stage.showingProperty().addListener((obs, oldValue, showing) -> {
                if (showing && !mapInitialized) {
                    initializeMap();
                }
            });
        });
    }

    private void initializeMap() {
        if (mapInitialized) {
            return;
        }

        mapInitialized = true;
        mapInitializationPending = false;

        var webEngine = mapWebView.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webEngine.setUserAgent("Mozilla/5.0 JavaFX WebView");
        jsbridge = new JavaScriptBridge(webEngine);

        // Load map HTML content
        String mapHtml = JavaScriptBridge.buildMapHtml();
        webEngine.loadContent(mapHtml);

        // Wait for page load
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    mapReady = true;
                    webEngine.executeScript("setTimeout(function(){ map.invalidateSize(true); }, 300);");
                    jsbridge.invalidateMapSize();
                    applyRolePermissions();
                    if (currentUsername == null) {
                        statusLabel.setText("Map ready. Please login");
                    }
                });
            }
        });

        mapWebView.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (mapReady) {
                Platform.runLater(() -> webEngine.executeScript("map.invalidateSize(true);"));
            }
        });

        mapWebView.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (mapReady) {
                Platform.runLater(() -> webEngine.executeScript("map.invalidateSize(true);"));
            }
        });
    }

    private LoginSession showLoginDialog() {
        Dialog<LoginSession> dialog = new Dialog<>();
        dialog.setTitle("Login");
        dialog.setHeaderText("Login to your account");

        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList("Customer", "Admin", "Delivery"));
        roleCombo.setValue("Customer");
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        Label helpLabel = new Label("Default users are available, or create a new account via Sign Up");
        helpLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.add(new Label("Role:"), 0, 0);
        grid.add(roleCombo, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(helpLabel, 0, 3, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Node loginDialogButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginDialogButton.addEventFilter(ActionEvent.ACTION, event -> {
            UserRole role = parseRole(roleCombo.getValue());
            String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
            String password = passwordField.getText() == null ? "" : passwordField.getText().trim();

            if (username.isEmpty() || password.isEmpty()) {
                event.consume();
                showError("Username and password are required");
                return;
            }

            if (!isValidCredential(role, username, password)) {
                event.consume();
                showError("Invalid credentials for selected role");
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new LoginSession(parseRole(roleCombo.getValue()), usernameField.getText().trim());
            }
            return null;
        });

        Optional<LoginSession> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private LoginSession showSignUpDialog() {
        Dialog<LoginSession> dialog = new Dialog<>();
        dialog.setTitle("Sign Up");
        dialog.setHeaderText("Create a new account");

        ButtonType signupButtonType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(signupButtonType, ButtonType.CANCEL);

        ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList("Customer", "Delivery"));
        roleCombo.setValue("Customer");
        TextField usernameField = new TextField();
        PasswordField passwordField = new PasswordField();
        PasswordField confirmField = new PasswordField();

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.add(new Label("Role:"), 0, 0);
        grid.add(roleCombo, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new Label("Password:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(new Label("Confirm:"), 0, 3);
        grid.add(confirmField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Node signupDialogButton = dialog.getDialogPane().lookupButton(signupButtonType);
        signupDialogButton.addEventFilter(ActionEvent.ACTION, event -> {
            String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
            String password = passwordField.getText() == null ? "" : passwordField.getText().trim();
            String confirm = confirmField.getText() == null ? "" : confirmField.getText().trim();

            if (username.length() < 3) {
                event.consume();
                showError("Username must be at least 3 characters");
                return;
            }

            if (password.length() < 4) {
                event.consume();
                showError("Password must be at least 4 characters");
                return;
            }

            if (!password.equals(confirm)) {
                event.consume();
                showError("Password and confirm password do not match");
                return;
            }

            if (userRepository.usernameExists(username)) {
                event.consume();
                showError("Username already exists");
                return;
            }

            UserRole role = parseRole(roleCombo.getValue());
            if (role == UserRole.ADMIN) {
                event.consume();
                showError("Admin signup is not allowed");
                return;
            }

            boolean created = userRepository.registerUser(username, password, role.name());
            if (!created) {
                event.consume();
                showError("Could not create account. Check database settings.");
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == signupButtonType) {
                return new LoginSession(parseRole(roleCombo.getValue()), usernameField.getText().trim());
            }
            return null;
        });

        Optional<LoginSession> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private UserRole parseRole(String roleText) {
        if ("Admin".equalsIgnoreCase(roleText)) {
            return UserRole.ADMIN;
        }
        if ("Delivery".equalsIgnoreCase(roleText)) {
            return UserRole.DELIVERY;
        }
        return UserRole.CUSTOMER;
    }

    private boolean isValidCredential(UserRole role, String username, String password) {
        return userRepository.authenticate(username, password, role.name());
    }

    private void applyRolePermissions() {
        boolean canRoute = mapReady && (currentUserRole == UserRole.CUSTOMER || currentUserRole == UserRole.ADMIN);
        sourceCombo.setDisable(!canRoute);
        destinationCombo.setDisable(!canRoute);
        bikeRadio.setDisable(!canRoute);
        vanRadio.setDisable(!canRoute);
        truckRadio.setDisable(!canRoute);
        strategyCombo.setDisable(!canRoute);
        findRouteButton.setDisable(!canRoute);

        saveRouteButton.setDisable(!(mapReady && currentUserRole == UserRole.ADMIN));
        viewHistoryButton.setDisable(!(mapReady && currentUserRole == UserRole.ADMIN));

        placeOrderButton.setDisable(!(mapReady && currentUserRole == UserRole.CUSTOMER));
        viewOrdersButton.setDisable(!(mapReady && (currentUserRole == UserRole.ADMIN || currentUserRole == UserRole.DELIVERY || currentUserRole == UserRole.CUSTOMER)));

        boolean deliveryActionsEnabled = mapReady && currentUserRole == UserRole.DELIVERY;
        acceptOrderButton.setDisable(!deliveryActionsEnabled);
        completeOrderButton.setDisable(!deliveryActionsEnabled);
        simulateMovementButton.setDisable(!deliveryActionsEnabled || activeDeliveryOrder == null);
    }

    private void findRoute() {
        if (currentUserRole != UserRole.CUSTOMER && currentUserRole != UserRole.ADMIN) {
            showError("Only customer/admin can calculate routes");
            return;
        }

        selectedSourceCity = sourceCombo.getValue();
        selectedDestinationCity = destinationCombo.getValue();

        if (selectedSourceCity == null || selectedDestinationCity == null) {
            showError("Please select both source and destination cities");
            return;
        }

        if (selectedSourceCity.equals(selectedDestinationCity)) {
            showError("Source and destination must be different");
            return;
        }

        City sourceCity = graph.getCityById(getCityIdByName(selectedSourceCity));
        City destCity = graph.getCityById(getCityIdByName(selectedDestinationCity));

        if (sourceCity == null || destCity == null) {
            showError("City not found");
            return;
        }

        // Get selected vehicle
        Vehicle vehicle = getSelectedVehicle();
        if (vehicle == null) {
            showError("Please select a vehicle");
            return;
        }

        // Get cost strategy
        String strategy = strategyCombo.getValue();
        com.deliveryroute.cost.CostStrategy costStrategy =
                "Toll-Free Route".equals(strategy) 
                    ? new TollFreeCostStrategy()
                    : new StandardCostStrategy();

        // Find route
        DijkstraOptimizer optimizer = new DijkstraOptimizer(graph, costStrategy);
        try {
            currentRoute = optimizer.findOptimalRoute(sourceCity, destCity, vehicle);
            displayRoute(currentRoute);
            applyRolePermissions();
            statusLabel.setText("Route found!");
        } catch (RouteNotFoundException e) {
            showError("No route found: " + e.getMessage());
            statusLabel.setText("No route available");
            currentRoute = null;
            saveRouteButton.setDisable(true);
        }
    }

    private void displayRoute(Route route) {
        // Clear previous route
        jsbridge.clearRoute();

        // Draw new route on map
        jsbridge.drawRoute(route);
        jsbridge.highlightCities(route);
        jsbridge.fitMapToCities(route);
        jsbridge.updateRouteInfoCard(
            route.getFormattedPath(),
            route.getTotalDistanceKm(),
            route.getTotalFuelCost(),
            route.getTotalTollCost(),
            route.getTotalTrafficPenalty(),
            route.getTotalCost()
        );

        // Display cost summary
        costSummaryText.setText(route.getCostSummary());
    }

    private void saveCurrentRoute() {
        if (currentRoute == null) {
            showError("No route to save");
            return;
        }

        if (currentUserRole != UserRole.ADMIN) {
            showError("Only admin can save route history");
            return;
        }

        try {
            routeRepository.saveRoute(currentRoute, selectedSourceCity, selectedDestinationCity);
            showInfo("Route saved successfully!");
        } catch (Exception e) {
            showError("Failed to save route: " + e.getMessage());
        }
    }

    private void showRouteHistory() {
        if (currentUserRole != UserRole.ADMIN) {
            showError("Only admin can view saved route history");
            return;
        }

        java.util.List<RouteRepository.RouteRecord> routes = routeRepository.getAllRoutes();

        if (routes.isEmpty()) {
            showInfo("No saved routes yet");
            return;
        }

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Route History");
        dialog.setHeaderText("Saved Routes");

        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        StringBuilder sb = new StringBuilder();
        for (RouteRepository.RouteRecord record : routes) {
            sb.append(record.toString()).append("\n");
        }
        textArea.setText(sb.toString());

        ScrollPane scrollPane = new ScrollPane(textArea);
        dialog.getDialogPane().setContent(scrollPane);
        dialog.showAndWait();
    }

    private void placeOrderFromCurrentRoute() {
        if (currentUserRole != UserRole.CUSTOMER) {
            showError("Only customer can place an order");
            return;
        }

        if (currentRoute == null || selectedSourceCity == null || selectedDestinationCity == null) {
            showError("Find a route first before placing an order");
            return;
        }

        try {
            orderRepository.placeOrder(currentRoute, selectedSourceCity, selectedDestinationCity, currentUsername);
            List<OrderRepository.OrderRecord> latest = orderRepository.getOrdersByCustomer(currentUsername);
            if (!latest.isEmpty()) {
                notificationService.notifyOrderPlaced(currentUsername, latest.get(0).getId());
            }
            showInfo("Order placed successfully");
            refreshOrderSummary();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void showOrdersForRole() {
        List<OrderRepository.OrderRecord> orders;
        if (currentUserRole == UserRole.ADMIN) {
            orders = orderRepository.getAllOrders();
        } else if (currentUserRole == UserRole.DELIVERY) {
            orders = orderRepository.getOrdersForDeliveryView(currentUsername);
        } else {
            orders = orderRepository.getOrdersByCustomer(currentUsername);
        }

        if (orders.isEmpty()) {
            showInfo("No orders available for this role");
            orderSummaryText.setText("No orders available");
            return;
        }

        if (currentUserRole == UserRole.CUSTOMER) {
            TableView<CustomerOrderRow> table = new TableView<>();

            TableColumn<CustomerOrderRow, Number> idCol = new TableColumn<>("Order ID");
            idCol.setCellValueFactory(c -> new javafx.beans.property.SimpleLongProperty(c.getValue().id));

            TableColumn<CustomerOrderRow, String> routeCol = new TableColumn<>("Route");
            routeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().route));

            TableColumn<CustomerOrderRow, String> timelineCol = new TableColumn<>("Timeline");
            timelineCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().timeline));

            TableColumn<CustomerOrderRow, String> etaCol = new TableColumn<>("Estimated vs Actual");
            etaCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().etaVsActual));

            table.getColumns().addAll(idCol, routeCol, timelineCol, etaCol);

            ObservableList<CustomerOrderRow> rows = FXCollections.observableArrayList();
            for (OrderRepository.OrderRecord order : orders) {
                String timeline = "Placed: " + safeTime(order.getPlacedAt())
                        + " -> Accepted: " + safeTime(order.getAcceptedAt())
                        + " -> Delivered: " + safeTime(order.getDeliveredAt());

                String etaVsActual = "ETA: " + (order.getEstimatedMinutes() == null ? "-" : order.getEstimatedMinutes() + " min")
                        + " | Actual: " + safeTime(order.getActualDelivery());

                rows.add(new CustomerOrderRow(
                        order.getId(),
                        order.getSourceCity() + " -> " + order.getDestinationCity(),
                        timeline,
                        etaVsActual
                ));
            }
            table.setItems(rows);

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Customer Order History");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setContent(table);
            dialog.showAndWait();
            return;
        }

        String content = formatOrders(orders);
        orderSummaryText.setText(content);
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Orders");
        dialog.setHeaderText("Order List");
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        dialog.getDialogPane().setContent(textArea);
        dialog.showAndWait();
    }

    private void acceptNextOrder() {
        if (currentUserRole != UserRole.DELIVERY) {
            showError("Only delivery user can accept orders");
            return;
        }

        try {
            OrderRepository.OrderRecord accepted = orderRepository.acceptNextOrder(currentUsername);
            if (accepted == null) {
                showInfo("No unassigned orders to accept");
                return;
            }

            activeDeliveryOrder = accepted;
            startTrackingForOrder(accepted);
            notificationService.notifyOrderAccepted(accepted.getCustomerUsername(), accepted.getId(), currentUsername);
            showInfo("Accepted order " + accepted.getId());
            refreshOrderSummary();
            applyRolePermissions();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void completeActiveOrder() {
        if (currentUserRole != UserRole.DELIVERY) {
            showError("Only delivery user can complete orders");
            return;
        }

        if (activeDeliveryOrder == null) {
            activeDeliveryOrder = orderRepository.getActiveAcceptedOrder(currentUsername);
            if (activeDeliveryOrder == null) {
                showInfo("No accepted order to complete");
                return;
            }
        }

        try {
            boolean completed = orderRepository.markOrderDelivered(activeDeliveryOrder.getId(), currentUsername);
            if (!completed) {
                showError("Could not mark this order as delivered");
                return;
            }

            showInfo("Completed order " + activeDeliveryOrder.getId());
            trackingService.stopTracking(activeDeliveryOrder.getId());
            notificationService.notifyOrderDelivered(activeDeliveryOrder.getCustomerUsername(), activeDeliveryOrder.getId());
            jsbridge.executeScript("if (map && map.deliveryMarker) { map.removeLayer(map.deliveryMarker); map.deliveryMarker = null; }");
            activeDeliveryOrder = null;
            trackingStatusLabel.setText("Tracking completed");
            refreshOrderSummary();
            applyRolePermissions();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void startTrackingForOrder(OrderRepository.OrderRecord order) {
        boolean started = trackingService.startTracking(
                order.getId(),
                currentUsername,
                order.getSourceCity(),
                order.getDestinationCity(),
                new TrackingService.TrackingListener() {
                    @Override
                    public void onTrackingUpdate(long orderId,
                                                 double lat,
                                                 double lng,
                                                 String eta,
                                                 double remainingDistanceKm,
                                                 double fraction) {
                        if (jsbridge != null) {
                            jsbridge.updateDeliveryMarker(lat, lng, eta);
                        }
                        trackingStatusLabel.setText(String.format(
                                "Current position: %.4f, %.4f | ETA: %s | %.2f km remaining",
                                lat,
                                lng,
                                eta,
                                remainingDistanceKm
                        ));
                    }

                    @Override
                    public void onTrackingCompleted(long orderId) {
                        trackingStatusLabel.setText("Delivery route simulation reached destination");
                    }
                }
        );

        if (started) {
            trackingStatusLabel.setText("Tracking started for order #" + order.getId());
        } else {
            trackingStatusLabel.setText("Tracking could not start");
        }
    }

    private void refreshCustomerTrackingLabel() {
        if (currentUserRole != UserRole.CUSTOMER || currentUsername == null) {
            return;
        }

        List<OrderRepository.OrderRecord> orders = orderRepository.getOrdersByCustomer(currentUsername);
        OrderRepository.OrderRecord activeOrder = null;
        for (OrderRepository.OrderRecord order : orders) {
            if ("ACCEPTED".equals(order.getStatus())) {
                activeOrder = order;
                break;
            }
        }

        if (activeOrder == null) {
            trackingStatusLabel.setText("No active delivery in transit");
            return;
        }

        OrderRepository.OrderRecord currentAcceptedOrder = activeOrder;

        TrackingRepository.TrackingUpdate latest = trackingRepository.getLatestUpdate(currentAcceptedOrder.getId());
        if (latest == null) {
            trackingStatusLabel.setText("Your delivery is being prepared");
            return;
        }

        City destinationCity = graph.getCities()
                .stream()
                .filter(city -> city.getName().equalsIgnoreCase(currentAcceptedOrder.getDestinationCity()))
                .findFirst()
                .orElse(null);
        if (destinationCity == null) {
            return;
        }

        double distanceKm = haversineKm(
                latest.lat(),
                latest.lng(),
                destinationCity.getLatitude(),
                destinationCity.getLongitude()
        );

        String etaText = "N/A";
        Integer estimatedMinutes = currentAcceptedOrder.getEstimatedMinutes();
        if (estimatedMinutes != null && estimatedMinutes > 0) {
            int remainingMinutes = (int) Math.max(1, Math.round((distanceKm / 40.0) * 60.0));
                etaText = java.time.LocalTime.now()
                    .plusMinutes(remainingMinutes)
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        trackingStatusLabel.setText(String.format("Your delivery is %.2f km away - ETA: %s", distanceKm, etaText));
        if (jsbridge != null) {
            jsbridge.updateDeliveryMarker(latest.lat(), latest.lng(), etaText);
        }
    }

    private void simulateDeliveryStep() {
        if (currentUserRole != UserRole.DELIVERY || activeDeliveryOrder == null) {
            showInfo("Accept an order first to simulate movement");
            return;
        }

        trackingService.simulateAdvance(activeDeliveryOrder.getId(), 0.1, new TrackingService.TrackingListener() {
            @Override
            public void onTrackingUpdate(long orderId,
                                         double lat,
                                         double lng,
                                         String eta,
                                         double remainingDistanceKm,
                                         double fraction) {
                if (jsbridge != null) {
                    jsbridge.updateDeliveryMarker(lat, lng, eta);
                }
                trackingStatusLabel.setText(String.format(
                        "Manual simulation: %.0f%% complete | %.2f km left | ETA: %s",
                        fraction * 100.0,
                        remainingDistanceKm,
                        eta
                ));
            }

            @Override
            public void onTrackingCompleted(long orderId) {
                trackingStatusLabel.setText("Manual simulation reached destination");
            }
        });
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private void refreshOrderSummary() {
        if (currentUserRole == null) {
            orderSummaryText.setText("Login required");
            return;
        }

        List<OrderRepository.OrderRecord> orders;
        if (currentUserRole == UserRole.ADMIN) {
            orders = orderRepository.getAllOrders();
        } else if (currentUserRole == UserRole.DELIVERY) {
            orders = orderRepository.getOrdersForDeliveryView(currentUsername);
        } else {
            orders = orderRepository.getOrdersByCustomer(currentUsername);
        }

        if (orders.isEmpty()) {
            orderSummaryText.setText("No orders yet");
            return;
        }

        orderSummaryText.setText(formatOrders(orders));
    }

    private String formatOrders(List<OrderRepository.OrderRecord> orders) {
        StringBuilder sb = new StringBuilder();
        for (OrderRepository.OrderRecord order : orders) {
            sb.append(order).append("\n");
        }
        return sb.toString();
    }

    @FXML
    private void openNotifications() {
        if (currentUsername == null) {
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Notifications");
        ButtonType markAllType = new ButtonType("Mark all read", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(markAllType, ButtonType.CLOSE);

        ListView<String> listView = new ListView<>();
        List<NotificationRepository.NotificationRecord> records = notificationService.latest(currentUsername, 10);
        for (NotificationRepository.NotificationRecord record : records) {
            String status = record.isRead() ? "" : "[NEW] ";
            listView.getItems().add(status + record.createdAt() + " - " + record.message());
        }
        dialog.getDialogPane().setContent(listView);

        dialog.setResultConverter(type -> {
            if (type == markAllType) {
                notificationService.markAllRead(currentUsername);
                unreadBadgeLabel.setText("0");
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void checkAndPromptPendingRatings() {
        if (currentUserRole != UserRole.CUSTOMER || currentUsername == null || ratingDialogOpen) {
            return;
        }

        List<OrderRepository.OrderRecord> pending = orderRepository.getDeliveredUnratedOrdersForCustomer(currentUsername);
        if (pending.isEmpty()) {
            return;
        }

        showRatingDialog(pending.get(0));
    }

    private void showRatingDialog(OrderRepository.OrderRecord deliveredOrder) {
        ratingDialogOpen = true;
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Rate your delivery");

        ButtonType submitType = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        ButtonType skipType = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(submitType, skipType);

        ToggleGroup starsGroup = new ToggleGroup();
        javafx.scene.layout.HBox starsBox = new javafx.scene.layout.HBox(6);
        for (int i = 1; i <= 5; i++) {
            javafx.scene.control.ToggleButton star = new javafx.scene.control.ToggleButton("★");
            star.setUserData(i);
            star.setToggleGroup(starsGroup);
            star.setStyle("-fx-font-size: 18; -fx-background-color: transparent; -fx-text-fill: #E5581E;");
            starsBox.getChildren().add(star);
        }

        TextArea reviewArea = new TextArea();
        reviewArea.setPromptText("Optional review (max 200 chars)");
        reviewArea.setPrefRowCount(4);
        reviewArea.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && newValue.length() > 200) {
                reviewArea.setText(newValue.substring(0, 200));
            }
        });

        VBox content = new VBox(10, new Label("Rate your delivery"), starsBox, reviewArea);
        dialog.getDialogPane().setContent(content);

        Node submitButton = dialog.getDialogPane().lookupButton(submitType);
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (starsGroup.getSelectedToggle() == null) {
                event.consume();
                showError("Please select a star rating");
            }
        });

        dialog.setResultConverter(type -> type == submitType);
        Optional<Boolean> result = dialog.showAndWait();
        if (result.isPresent() && result.get()) {
            int rating = (int) starsGroup.getSelectedToggle().getUserData();
            String review = reviewArea.getText() == null ? "" : reviewArea.getText().trim();
            ratingRepository.addRating(
                    deliveredOrder.getId(),
                    deliveredOrder.getCustomerUsername(),
                    deliveredOrder.getAssignedDeliveryUsername(),
                    rating,
                    review
            );
            notificationService.markAllRead(currentUsername);
        }

        ratingDialogOpen = false;
    }

    private String safeTime(String raw) {
        return raw == null || raw.isBlank() ? "-" : raw;
    }

    private record CustomerOrderRow(long id,
                                    String route,
                                    String timeline,
                                    String etaVsActual) {
    }

    private Vehicle getSelectedVehicle() {
        Toggle selected = vehicleGroup.getSelectedToggle();
        if (selected == null) return null;

        String vehicleType = (String) selected.getUserData();
        return switch (vehicleType) {
            case "Bike" -> new Bike();
            case "Van" -> new Van();
            case "Truck" -> new Truck();
            default -> null;
        };
    }

    private int getCityIdByName(String cityName) {
        for (City city : graph.getCities()) {
            if (city.getName().equals(cityName)) {
                return city.getId();
            }
        }
        return -1;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Info");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String getMapHtmlContent() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="X-UA-Compatible" content="IE=edge">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Delivery Route Map</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
                <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body, #map { margin: 0; padding: 0; width: 100%; height: 100vh; overflow: hidden; }
                    html, body { font-family: Arial, sans-serif; }
                    body { position: relative; }
                    #map { position: absolute; inset: 0; background: #e0e0e0; }
                    .leaflet-popup-content { font-size: 14px; font-weight: bold; }
                    .leaflet-control-zoom { z-index: 1000; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    let map = null;
                    let routePolyline = null;
                    let routeMarkers = [];
                    let cityMarkers = [];
                    let cityMarkerMap = {};

                    function ensureMapSize() {
                        if (map) {
                            map.invalidateSize(true);
                        }
                    }

                    function initMap() {
                        console.log("Initializing map...");
                        if (map !== null) return;
                        
                        map = L.map('map', {
                            center: [20.5, 76.5],
                            zoom: 5,
                            zoomControl: true,
                            scrollWheelZoom: true
                        });

                        const osmLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            attribution: '© OpenStreetMap contributors',
                            subdomains: ['a','b','c'],
                            maxZoom: 18,
                            minZoom: 3
                        });

                        let fallbackApplied = false;
                        osmLayer.on('tileerror', function () {
                            if (!fallbackApplied) {
                                fallbackApplied = true;
                                map.removeLayer(osmLayer);
                                L.tileLayer('https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png', {
                                    attribution: '© CartoDB'
                                }).addTo(map);
                            }
                        });
                        osmLayer.addTo(map);
                        
                        addCityMarkers();
                        setTimeout(ensureMapSize, 100);
                        console.log("Map initialized successfully");
                    }
                    
                    function addCityMarkers() {
                        const cities = [
                            {name: 'Mumbai', lat: 19.0760, lng: 72.8777},
                            {name: 'Pune', lat: 18.5204, lng: 73.8567},
                            {name: 'Aurangabad', lat: 19.8762, lng: 75.3433},
                            {name: 'Nashik', lat: 19.9975, lng: 73.7898},
                            {name: 'Nagpur', lat: 21.1458, lng: 79.0882},
                            {name: 'Ahmedabad', lat: 23.0225, lng: 72.5714},
                            {name: 'Indore', lat: 22.7196, lng: 75.8577},
                            {name: 'Bhopal', lat: 23.1815, lng: 79.9864}
                        ];
                        
                        cities.forEach(city => {
                            let marker = L.circleMarker([city.lat, city.lng], {
                                radius: 12,
                                fillColor: '#FFA500',
                                color: '#D97E00',
                                weight: 3,
                                opacity: 1,
                                fillOpacity: 0.95
                            }).bindPopup('<b>' + city.name + '</b>')
                              .addTo(map);
                            
                            cityMarkers.push(marker);
                            cityMarkerMap[city.name] = marker;
                            
                            // Add city label in a separate container
                            let labelIcon = L.divIcon({
                                className: 'city-label',
                                html: '<div style="background: #FFEB3B; padding: 2px 6px; border-radius: 3px; font-weight: bold; font-size: 11px; white-space: nowrap; border: 1px solid #F57F17;">' + city.name + '</div>',
                                iconSize: [80, 20],
                                iconAnchor: [40, 35],
                                popupAnchor: [0, -35]
                            });
                            
                            L.marker([city.lat, city.lng], {icon: labelIcon}).addTo(map);
                        });
                        console.log("Added " + cities.length + " city markers");
                    }

                    function drawRoute(coords, color, label) {
                        console.log("Drawing route with " + coords.length + " points, color=" + color);
                        ensureMapSize();
                        clearRoute();
                        
                        if (coords && coords.length > 0) {
                            routePolyline = L.polyline(coords, {
                                color: color,
                                weight: 5,
                                opacity: 0.85,
                                dashArray: '10, 5',
                                lineCap: 'round',
                                lineJoin: 'round'
                            }).addTo(map);

                            for (let i = 0; i < coords.length; i++) {
                                let circleMarker = L.circleMarker(coords[i], {
                                    radius: 10,
                                    fillColor: color,
                                    color: '#FFFFFF',
                                    weight: 3,
                                    opacity: 1,
                                    fillOpacity: 0.95
                                }).bindPopup('Stop ' + (i + 1))
                                  .addTo(map);
                                  
                                routeMarkers.push(circleMarker);
                            }
                            
                            // Fit map to route bounds
                            if (routeMarkers.length > 0) {
                                let group = new L.featureGroup(routeMarkers);
                                let bounds = group.getBounds();
                                if (bounds.isValid()) {
                                    map.fitBounds(bounds, {padding: [80, 80]});
                                }
                            }
                            console.log("Route drawn successfully");
                        }
                    }

                    function clearRoute() {
                        if (routePolyline && map.hasLayer(routePolyline)) {
                            map.removeLayer(routePolyline);
                        }
                        routePolyline = null;
                        
                        routeMarkers.forEach(marker => {
                            if (map && map.hasLayer(marker)) {
                                map.removeLayer(marker);
                            }
                        });
                        routeMarkers = [];
                        console.log("Route cleared");
                    }

                    function highlightCities(cityNames) {
                        cityMarkers.forEach(marker => {
                            marker.setStyle({fillColor: '#FFA500'});
                        });
                    }

                    function fitToCities() {
                        if (map && routeMarkers.length > 0) {
                            let group = new L.featureGroup(routeMarkers);
                            let bounds = group.getBounds();
                            if (bounds.isValid()) {
                                map.fitBounds(bounds, {padding: [80, 80]});
                            }
                        }
                    }

                    function updateDeliveryMarker(lat, lng, eta) {
                        if (!map) {
                            return;
                        }

                        if (!map.deliveryMarker) {
                            map.deliveryMarker = L.marker([lat, lng]).addTo(map);
                            map.etaPopup = L.popup({closeButton: false, autoClose: false, closeOnClick: false});
                        }

                        map.deliveryMarker.setLatLng([lat, lng]);
                        if (map.etaPopup) {
                            map.etaPopup
                                .setLatLng([lat, lng])
                                .setContent('ETA: ' + eta)
                                .openOn(map);
                        }
                    }
                    
                    // Initialize map when page loads
                    window.initMap = initMap;
                    document.addEventListener('DOMContentLoaded', initMap);
                    window.addEventListener('resize', ensureMapSize);
                    setTimeout(initMap, 500);
                </script>
            </body>
            </html>
            """;
    }
}
