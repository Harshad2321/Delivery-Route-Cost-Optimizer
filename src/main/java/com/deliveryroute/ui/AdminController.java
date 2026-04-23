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
        loadOrders();
        loadUsers();
        refreshDashboard();
        refreshAnalytics();
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
                    case "DELIVERY" -> setStyle("-fx-font-weight: bold; -fx-text-fill: #F97316;");
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
                    user.username(),
                    user.role(),
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
