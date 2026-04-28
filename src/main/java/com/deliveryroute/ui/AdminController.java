package com.deliveryroute.ui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.deliveryroute.database.NotificationRepository;
import com.deliveryroute.database.OrderRepository;
import com.deliveryroute.database.RatingRepository;
import com.deliveryroute.database.UserRepository;
import com.deliveryroute.database.AdminConfigRepository;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Controller for professional-grade admin dashboard.
 */
public class AdminController {

    @FXML private Label adminUserLabel;

    @FXML private Button dashboardNavButton;
    @FXML private Button ordersNavButton;
    @FXML private Button usersNavButton;
    @FXML private Button analyticsNavButton;
    @FXML private Button settingsNavButton;

    @FXML private StackPane contentStack;
    @FXML private VBox dashboardPane;
    @FXML private VBox ordersPane;
    @FXML private VBox usersPane;
    @FXML private VBox analyticsPane;
    @FXML private VBox settingsPane;

    @FXML private ComboBox<String> adminVehicleTypeCombo;
    @FXML private TextField adminVehicleMaxWeightField;
    @FXML private TextField adminVehicleMaxDistanceField;
    @FXML private TextField adminVehiclePricePerKmField;
    @FXML private TextField adminVehicleTollField;
    @FXML private TextField adminVehicleProfitField;
    @FXML private TextArea vehicleConfigsTextArea;

    @FXML private TextField adminItemTypeNameField;
    @FXML private TextField adminItemAllowedVehiclesField;
    @FXML private TextField adminItemBasePriceField;
    @FXML private TextField adminItemTollThresholdField;
    @FXML private TextArea itemTypesTextArea;

    @FXML private TextField adminPricingRuleNameField;
    @FXML private TextField adminPricingBasePriceField;
    @FXML private TextField adminPricingTollThresholdField;
    @FXML private TextField adminPricingTollAmountField;
    @FXML private TextArea pricingRulesTextArea;

    @FXML private TextField adminManageOrderIdField;
    @FXML private ComboBox<String> adminManageOrderStatusCombo;

    @FXML private Label totalOrdersTodayLabel;
    @FXML private Label revenueTodayLabel;
    @FXML private Label activeDeliveriesLabel;
    @FXML private Label avgRatingLabel;

    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private TextField customerSearchField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;

    @FXML private TableView<OrderRow> ordersTable;
    @FXML private TableColumn<OrderRow, Number> orderIdColumn;
    @FXML private TableColumn<OrderRow, String> orderCustomerColumn;
    @FXML private TableColumn<OrderRow, String> orderRouteColumn;
    @FXML private TableColumn<OrderRow, String> orderVehicleColumn;
    @FXML private TableColumn<OrderRow, Number> orderCostColumn;
    @FXML private TableColumn<OrderRow, String> orderStatusColumn;
    @FXML private TableColumn<OrderRow, String> orderDeliveryColumn;
    @FXML private TableColumn<OrderRow, String> orderPlacedAtColumn;
    @FXML private TableColumn<OrderRow, Void> orderAssignColumn;

    @FXML private TableView<UserRow> usersTable;
    @FXML private TableColumn<UserRow, String> userUsernameColumn;
    @FXML private TableColumn<UserRow, String> userRoleColumn;
    @FXML private TableColumn<UserRow, String> userRegisteredColumn;
    @FXML private TableColumn<UserRow, String> userStatusColumn;
    @FXML private TableColumn<UserRow, Number> userOrdersCountColumn;
    @FXML private TableColumn<UserRow, Void> userActionColumn;

    @FXML private PieChart ordersStatusPieChart;
    @FXML private BarChart<String, Number> revenueBarChart;
    @FXML private BarChart<String, Number> ratingBarChart;

    private final OrderRepository orderRepository = new OrderRepository();
    private final UserRepository userRepository = new UserRepository();
    private final RatingRepository ratingRepository = new RatingRepository();
    private final NotificationRepository notificationRepository = new NotificationRepository();
    private final AdminConfigRepository adminConfigRepository = new AdminConfigRepository();

    private final ObservableList<OrderRow> allOrders = FXCollections.observableArrayList();
    private FilteredList<OrderRow> filteredOrders;
    private final ObservableList<UserRow> allUsers = FXCollections.observableArrayList();

    private ScheduledExecutorService scheduler;

    @FXML
    public void initialize() {
        setupNavigation();
        setupOrderTable();
        setupUserTable();
        setupFilters();
        setupAdminCrudPanel();
        loadOrders();
        loadUsers();
        refreshDashboard();
        refreshAnalytics();
        refreshAdminCrudData();
        startAutoRefresh();

        Platform.runLater(() -> {
            Stage stage = (Stage) contentStack.getScene().getWindow();
            stage.setOnCloseRequest(e -> shutdown());
        });
    }

    public void setAdminUser(String username) {
        adminUserLabel.setText("Signed in as " + username);
    }

    private void setupNavigation() {
        activatePane(dashboardPane);
        activateNavButton(dashboardNavButton);
        styleNavButton(dashboardNavButton);
        styleNavButton(ordersNavButton);
        styleNavButton(usersNavButton);
        styleNavButton(analyticsNavButton);
        styleNavButton(settingsNavButton);
    }

    private void styleNavButton(Button button) {
        button.setStyle("-fx-alignment: CENTER_LEFT; -fx-font-size: 14; -fx-background-color: transparent; -fx-text-fill: #CBD5E1; -fx-cursor: hand;");
    }

    @FXML
    private void showDashboard(ActionEvent ignored) {
        activatePane(dashboardPane);
        activateNavButton(dashboardNavButton);
    }

    @FXML
    private void showOrders(ActionEvent ignored) {
        activatePane(ordersPane);
        activateNavButton(ordersNavButton);
    }

    @FXML
    private void showUsers(ActionEvent ignored) {
        activatePane(usersPane);
        activateNavButton(usersNavButton);
    }

    @FXML
    private void showAnalytics(ActionEvent ignored) {
        activatePane(analyticsPane);
        activateNavButton(analyticsNavButton);
    }

    @FXML
    private void showSettings(ActionEvent ignored) {
        activatePane(settingsPane);
        activateNavButton(settingsNavButton);
    }

    private void activatePane(VBox pane) {
        dashboardPane.setVisible(false);
        dashboardPane.setManaged(false);
        ordersPane.setVisible(false);
        ordersPane.setManaged(false);
        usersPane.setVisible(false);
        usersPane.setManaged(false);
        analyticsPane.setVisible(false);
        analyticsPane.setManaged(false);
        settingsPane.setVisible(false);
        settingsPane.setManaged(false);

        pane.setVisible(true);
        pane.setManaged(true);
    }

    private void activateNavButton(Button activeButton) {
        List<Button> buttons = List.of(dashboardNavButton, ordersNavButton, usersNavButton, analyticsNavButton, settingsNavButton);
        for (Button button : buttons) {
            if (button == activeButton) {
                button.setStyle("-fx-alignment: CENTER_LEFT; -fx-font-size: 14; -fx-background-color: #334155; -fx-text-fill: #F8FAFC; -fx-font-weight: bold; -fx-background-radius: 6;");
            } else {
                styleNavButton(button);
            }
        }
    }

    private void setupOrderTable() {
        orderIdColumn.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().id));
        orderCustomerColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().customer));
        orderRouteColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().route));
        orderVehicleColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().vehicle));
        orderCostColumn.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().cost));
        orderStatusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));
        orderDeliveryColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().deliveryPerson));
        orderPlacedAtColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().placedAt));

        orderStatusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                switch (item) {
                    case "PLACED" -> setStyle("-fx-font-weight: bold; -fx-text-fill: #F97316;");
                    case "ACCEPTED" -> setStyle("-fx-font-weight: bold; -fx-text-fill: #2563EB;");
                    case "DELIVERED" -> setStyle("-fx-font-weight: bold; -fx-text-fill: #16A34A;");
                    default -> setStyle("");
                }
            }
        });

        ordersTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(OrderRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("status-placed", "status-accepted", "status-delivered");
                if (empty || row == null) {
                    setStyle("");
                    return;
                }
                switch (row.status) {
                    case "PLACED" -> {
                        getStyleClass().add("status-placed");
                        setStyle("-fx-background-color: #FFF7ED;");
                    }
                    case "ACCEPTED" -> {
                        getStyleClass().add("status-accepted");
                        setStyle("-fx-background-color: #EFF6FF;");
                    }
                    case "DELIVERED" -> {
                        getStyleClass().add("status-delivered");
                        setStyle("-fx-background-color: #F0FDF4;");
                    }
                    default -> setStyle("");
                }
            }
        });

        orderAssignColumn.setCellFactory(col -> new TableCell<>() {
            private final Button assignButton = new Button("Assign");
            {
                assignButton.setOnAction(e -> {
                    OrderRow row = getTableView().getItems().get(getIndex());
                    assignOrder(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                OrderRow row = getTableView().getItems().get(getIndex());
                assignButton.setDisable(!"PLACED".equals(row.status));
                setGraphic(assignButton);
            }
        });
    }

    private void setupFilters() {
        statusFilterCombo.setItems(FXCollections.observableArrayList("ALL", "PLACED", "ACCEPTED", "DELIVERED"));
        statusFilterCombo.setValue("ALL");

        filteredOrders = new FilteredList<>(allOrders, r -> true);
        SortedList<OrderRow> sorted = new SortedList<>(filteredOrders);
        sorted.comparatorProperty().bind(ordersTable.comparatorProperty());
        ordersTable.setItems(sorted);

        statusFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> applyOrderFilter());
        customerSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyOrderFilter());
        startDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> applyOrderFilter());
        endDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> applyOrderFilter());
    }

    private void applyOrderFilter() {
        String selectedStatus = statusFilterCombo.getValue();
        String customerQuery = customerSearchField.getText() == null ? "" : customerSearchField.getText().trim().toLowerCase();
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        filteredOrders.setPredicate(row -> {
            boolean statusMatch = selectedStatus == null || "ALL".equals(selectedStatus) || selectedStatus.equals(row.status);
            boolean customerMatch = customerQuery.isEmpty() || row.customer.toLowerCase().contains(customerQuery);
            LocalDate orderDate = parseDate(row.placedAt);
            boolean startMatch = startDate == null || (orderDate != null && !orderDate.isBefore(startDate));
            boolean endMatch = endDate == null || (orderDate != null && !orderDate.isAfter(endDate));
            return statusMatch && customerMatch && startMatch && endMatch;
        });
    }

    @FXML
    private void clearOrderFilters(ActionEvent ignored) {
        statusFilterCombo.setValue("ALL");
        customerSearchField.clear();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        applyOrderFilter();
    }

    @FXML
    private void exportOrdersCsv(ActionEvent ignored) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Orders CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("orders_export.csv");
        File file = chooser.showSaveDialog(contentStack.getScene().getWindow());
        if (file == null) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("ID,Customer,Route,Vehicle,Cost,Status,Delivery Person,Placed At\n");
            for (OrderRow row : ordersTable.getItems()) {
                writer.write(row.id + "," + csv(row.customer) + "," + csv(row.route) + "," + csv(row.vehicle) + ","
                        + String.format("%.2f", row.cost) + "," + row.status + "," + csv(row.deliveryPerson) + "," + csv(row.placedAt) + "\n");
            }
            showInfo("CSV exported successfully.");
        } catch (IOException e) {
            showError("Failed to export CSV: " + e.getMessage());
        }
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private void assignOrder(OrderRow row) {
        List<String> deliveryUsers = userRepository.getActiveDeliveryUsers();
        if (deliveryUsers.isEmpty()) {
            showError("No active delivery users available");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Assign Delivery Person");
        dialog.setHeaderText("Assign order #" + row.id);

        ButtonType assignType = new ButtonType("Assign", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(assignType, ButtonType.CANCEL);

        ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(deliveryUsers));
        comboBox.setValue(deliveryUsers.get(0));
        dialog.getDialogPane().setContent(comboBox);

        dialog.setResultConverter(buttonType -> buttonType == assignType ? comboBox.getValue() : null);
        dialog.showAndWait().ifPresent(selectedUser -> {
            boolean updated = orderRepository.assignOrder(row.id, selectedUser);
            if (updated) {
                notificationRepository.addNotification(selectedUser, "New order assigned: #" + row.id);
                refreshAllSections();
                showInfo("Order assigned to " + selectedUser);
            } else {
                showError("Could not assign order. It may already be assigned.");
            }
        });
    }

    private void setupUserTable() {
        userUsernameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username));
        userRoleColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().role));
        userRegisteredColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().registered));
        userStatusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().active ? "ACTIVE" : "INACTIVE"));
        userOrdersCountColumn.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().ordersCount));

        userRoleColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                switch (item) {
                    case "CUSTOMER" -> setStyle("-fx-font-weight: bold; -fx-text-fill: #0D9488;");
                    case "DELIVERY", "DELIVERY_PARTNER" -> setStyle("-fx-font-weight: bold; -fx-text-fill: #F97316;");
                    case "ADMIN" -> setStyle("-fx-font-weight: bold; -fx-text-fill: #7C3AED;");
                    default -> setStyle("");
                }
            }
        });

        userActionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button toggleButton = new Button();
            {
                toggleButton.setOnAction(e -> {
                    UserRow row = getTableView().getItems().get(getIndex());
                    boolean target = !row.active;
                    boolean ok = userRepository.setUserActive(row.username, target);
                    if (ok) {
                        loadUsers();
                    } else {
                        showError("Could not update user status.");
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                UserRow row = getTableView().getItems().get(getIndex());
                toggleButton.setText(row.active ? "Deactivate" : "Activate");
                setGraphic(toggleButton);
            }
        });
    }

    private void loadOrders() {
        allOrders.clear();
        for (OrderRepository.OrderRecord record : orderRepository.getAllOrders()) {
            allOrders.add(new OrderRow(
                    record.getId(),
                    record.getCustomerUsername(),
                    record.getSourceCity() + " -> " + record.getDestinationCity(),
                    record.getVehicleType(),
                    record.getTotalCost(),
                    record.getStatus(),
                    record.getAssignedDeliveryUsername() == null ? "-" : record.getAssignedDeliveryUsername(),
                    record.getPlacedAt()
            ));
        }
        applyOrderFilter();
    }

    private void loadUsers() {
        allUsers.clear();
        for (UserRepository.UserRecord user : userRepository.getAllUsersWithOrderCount()) {
            allUsers.add(new UserRow(
                    user.identifier(),
                    user.accountType(),
                    user.createdAt(),
                    user.active(),
                    user.ordersCount()
            ));
        }
        usersTable.setItems(allUsers);
    }

    private void refreshDashboard() {
        totalOrdersTodayLabel.setText(String.valueOf(orderRepository.getTodaysOrderCount()));
        revenueTodayLabel.setText("INR " + String.format("%.2f", orderRepository.getTodaysRevenue()));
        activeDeliveriesLabel.setText(String.valueOf(orderRepository.getActiveDeliveriesCount()));
        avgRatingLabel.setText(String.format("%.2f", ratingRepository.getWeeklyAverageRating()));
    }

    private void refreshAnalytics() {
        Map<String, Long> statusCounts = orderRepository.getStatusBreakdown();
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
                new PieChart.Data("PLACED", statusCounts.getOrDefault("PLACED", 0L)),
                new PieChart.Data("ACCEPTED", statusCounts.getOrDefault("ACCEPTED", 0L)),
                new PieChart.Data("DELIVERED", statusCounts.getOrDefault("DELIVERED", 0L))
        );
        ordersStatusPieChart.setData(pieData);

        XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();
        revenueSeries.setName("Revenue");
        for (OrderRepository.DailyRevenuePoint point : orderRepository.getRevenueLast7Days()) {
            revenueSeries.getData().add(new XYChart.Data<>(point.date(), point.revenue()));
        }
        revenueBarChart.getData().clear();
        revenueBarChart.getData().add(revenueSeries);

        XYChart.Series<String, Number> ratingSeries = new XYChart.Series<>();
        ratingSeries.setName("Avg Rating");
        for (RatingRepository.DeliveryRatingPoint point : ratingRepository.getAverageRatingsByDeliveryUser()) {
            ratingSeries.getData().add(new XYChart.Data<>(point.deliveryUser(), point.averageRating()));
        }
        ratingBarChart.getData().clear();
        ratingBarChart.getData().add(ratingSeries);
    }

    private void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::refreshAllSections), 30, 30, TimeUnit.SECONDS);
    }

    private void refreshAllSections() {
        loadOrders();
        loadUsers();
        refreshDashboard();
        refreshAnalytics();
        refreshAdminCrudData();
    }

    private void setupAdminCrudPanel() {
        if (adminVehicleTypeCombo != null) {
            adminVehicleTypeCombo.setItems(FXCollections.observableArrayList("Bike", "Van", "Truck"));
            adminVehicleTypeCombo.setValue("Bike");
        }

        if (adminManageOrderStatusCombo != null) {
            adminManageOrderStatusCombo.setItems(FXCollections.observableArrayList("PLACED", "ACCEPTED", "DELIVERED", "CANCELLED"));
            adminManageOrderStatusCombo.setValue("PLACED");
        }
    }

    private void refreshAdminCrudData() {
        if (vehicleConfigsTextArea != null) {
            StringBuilder sb = new StringBuilder();
            for (AdminConfigRepository.VehicleConfig config : adminConfigRepository.getVehicleConfigs()) {
                sb.append(config.vehicleType())
                  .append(" | maxWeight=").append(String.format("%.1f", config.maxWeightKg())).append("kg")
                  .append(" | maxDistance=").append(String.format("%.1f", config.maxDistanceKm())).append("km")
                  .append(" | price/km=INR ").append(String.format("%.2f", config.pricePerKm()))
                  .append(" | toll=INR ").append(String.format("%.2f", config.tollCharge()))
                  .append(" | driverProfit=").append(String.format("%.2f", config.driverProfit())).append("%")
                  .append("\n");
            }
            vehicleConfigsTextArea.setText(sb.length() == 0 ? "No vehicle configs found" : sb.toString());
        }

        if (itemTypesTextArea != null) {
            StringBuilder sb = new StringBuilder();
            for (AdminConfigRepository.ItemTypeConfig config : adminConfigRepository.getItemTypes()) {
                sb.append(config.name())
                  .append(" | vehicles=").append(config.allowedVehicles())
                  .append(" | basePrice/km=INR ").append(String.format("%.2f", config.basePricePerKm()))
                  .append(" | tollThreshold=").append(String.format("%.1f", config.tollThresholdKm())).append("km")
                  .append("\n");
            }
            itemTypesTextArea.setText(sb.length() == 0 ? "No item categories found" : sb.toString());
        }

        if (pricingRulesTextArea != null) {
            StringBuilder sb = new StringBuilder();
            for (AdminConfigRepository.PricingRuleConfig config : adminConfigRepository.getPricingRules()) {
                sb.append(config.ruleName())
                  .append(" | basePrice/km=INR ").append(String.format("%.2f", config.basePricePerKm()))
                  .append(" | tollThreshold=").append(String.format("%.1f", config.tollThresholdKm())).append("km")
                  .append(" | toll=INR ").append(String.format("%.2f", config.tollAmount()))
                  .append("\n");
            }
            pricingRulesTextArea.setText(sb.length() == 0 ? "No pricing rules found" : sb.toString());
        }
    }

    @FXML
    private void addOrUpdateVehicleConfig(ActionEvent ignored) {
        try {
            String vehicleType = adminVehicleTypeCombo.getValue();
            double maxWeight = parsePositiveDouble(adminVehicleMaxWeightField.getText(), "Max weight");
            double maxDistance = parsePositiveDouble(adminVehicleMaxDistanceField.getText(), "Max distance");
            double pricePerKm = parsePositiveDouble(adminVehiclePricePerKmField.getText(), "Price per km");
            double toll = parseNonNegativeDouble(adminVehicleTollField.getText(), "Toll");
            double driverProfit = parseNonNegativeDouble(adminVehicleProfitField.getText(), "Driver profit");

            boolean ok = adminConfigRepository.upsertVehicleConfig(
                    new AdminConfigRepository.VehicleConfig(vehicleType, maxWeight, maxDistance, pricePerKm, toll, driverProfit)
            );
            if (!ok) {
                showError("Could not save vehicle config");
                return;
            }

            refreshAdminCrudData();
            showInfo("Vehicle config saved for " + vehicleType);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void deleteVehicleConfig(ActionEvent ignored) {
        String vehicleType = adminVehicleTypeCombo.getValue();
        if (vehicleType == null || vehicleType.isBlank()) {
            showError("Select a vehicle type to delete");
            return;
        }
        boolean ok = adminConfigRepository.deleteVehicleConfig(vehicleType);
        if (!ok) {
            showError("Vehicle config not found for " + vehicleType);
            return;
        }
        refreshAdminCrudData();
        showInfo("Vehicle config deleted for " + vehicleType);
    }

    @FXML
    private void addOrUpdateItemType(ActionEvent ignored) {
        try {
            String name = safeText(adminItemTypeNameField.getText());
            String allowedVehicles = safeText(adminItemAllowedVehiclesField.getText());
            if (name.isBlank()) {
                throw new IllegalArgumentException("Item type name is required");
            }
            if (allowedVehicles.isBlank()) {
                throw new IllegalArgumentException("Allowed vehicles are required (example: Van,Truck)");
            }

            double basePrice = parsePositiveDouble(adminItemBasePriceField.getText(), "Item base price per km");
            double threshold = parsePositiveDouble(adminItemTollThresholdField.getText(), "Item toll threshold");

            boolean ok = adminConfigRepository.upsertItemType(
                    new AdminConfigRepository.ItemTypeConfig(name, allowedVehicles, basePrice, threshold)
            );
            if (!ok) {
                showError("Could not save item type");
                return;
            }

            refreshAdminCrudData();
            showInfo("Item type saved: " + name);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void deleteItemType(ActionEvent ignored) {
        String name = safeText(adminItemTypeNameField.getText());
        if (name.isBlank()) {
            showError("Enter item type name to delete");
            return;
        }
        boolean ok = adminConfigRepository.deleteItemType(name);
        if (!ok) {
            showError("Item type not found: " + name);
            return;
        }
        refreshAdminCrudData();
        showInfo("Item type deleted: " + name);
    }

    @FXML
    private void addOrUpdatePricingRule(ActionEvent ignored) {
        try {
            String ruleName = safeText(adminPricingRuleNameField.getText());
            if (ruleName.isBlank()) {
                throw new IllegalArgumentException("Pricing rule name is required");
            }

            double basePrice = parsePositiveDouble(adminPricingBasePriceField.getText(), "Rule base price per km");
            double tollThreshold = parsePositiveDouble(adminPricingTollThresholdField.getText(), "Rule toll threshold");
            double tollAmount = parseNonNegativeDouble(adminPricingTollAmountField.getText(), "Rule toll amount");

            boolean ok = adminConfigRepository.upsertPricingRule(
                    new AdminConfigRepository.PricingRuleConfig(ruleName, basePrice, tollThreshold, tollAmount)
            );
            if (!ok) {
                showError("Could not save pricing rule");
                return;
            }

            refreshAdminCrudData();
            showInfo("Pricing rule saved: " + ruleName);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void deletePricingRule(ActionEvent ignored) {
        String ruleName = safeText(adminPricingRuleNameField.getText());
        if (ruleName.isBlank()) {
            showError("Enter pricing rule name to delete");
            return;
        }
        boolean ok = adminConfigRepository.deletePricingRule(ruleName);
        if (!ok) {
            showError("Pricing rule not found: " + ruleName);
            return;
        }
        refreshAdminCrudData();
        showInfo("Pricing rule deleted: " + ruleName);
    }

    @FXML
    private void updateOrderStatusByAdmin(ActionEvent ignored) {
        try {
            long orderId = parsePositiveLong(adminManageOrderIdField.getText(), "Order ID");
            String status = adminManageOrderStatusCombo.getValue();
            if (status == null || status.isBlank()) {
                showError("Select a status");
                return;
            }
            boolean ok = orderRepository.updateOrderStatusByAdmin(orderId, status);
            if (!ok) {
                showError("Order not found or update failed");
                return;
            }
            refreshAllSections();
            showInfo("Order #" + orderId + " updated to " + status);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void deleteOrderByAdmin(ActionEvent ignored) {
        try {
            long orderId = parsePositiveLong(adminManageOrderIdField.getText(), "Order ID");
            boolean ok = orderRepository.deleteOrderByAdmin(orderId);
            if (!ok) {
                showError("Order not found or delete failed");
                return;
            }
            refreshAllSections();
            showInfo("Order #" + orderId + " deleted");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private double parsePositiveDouble(String value, String fieldName) {
        double parsed = parseDouble(value, fieldName);
        if (parsed <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
        return parsed;
    }

    private double parseNonNegativeDouble(String value, String fieldName) {
        double parsed = parseDouble(value, fieldName);
        if (parsed < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }
        return parsed;
    }

    private double parseDouble(String value, String fieldName) {
        try {
            return Double.parseDouble(safeText(value));
        } catch (Exception ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid number");
        }
    }

    private long parsePositiveLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(safeText(value));
            if (parsed <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than 0");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid number");
        }
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private LocalDate parseDate(String timestampText) {
        if (timestampText == null || timestampText.isBlank()) {
            return null;
        }

        try {
            return Timestamp.valueOf(timestampText).toLocalDateTime().toLocalDate();
        } catch (IllegalArgumentException ex) {
            try {
                return LocalDateTime.parse(timestampText.replace(" ", "T")).toLocalDate();
            } catch (Exception ignored) {
                return null;
            }
        }
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

    private record OrderRow(long id,
                            String customer,
                            String route,
                            String vehicle,
                            double cost,
                            String status,
                            String deliveryPerson,
                            String placedAt) {
    }

    private record UserRow(String username,
                           String role,
                           String registered,
                           boolean active,
                           int ordersCount) {
    }
}
