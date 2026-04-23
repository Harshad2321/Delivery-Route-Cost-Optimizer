package com.deliveryroute.ui;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.deliveryroute.algorithm.Graph;
import com.deliveryroute.bridge.JavaScriptBridge;
import com.deliveryroute.database.NotificationRepository;
import com.deliveryroute.database.OrderRepository;
import com.deliveryroute.database.TrackingRepository;
import com.deliveryroute.database.UserRepository;
import com.deliveryroute.service.NotificationService;
import com.deliveryroute.service.TrackingService;

import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Dedicated delivery-user view controller.
 */
public class DeliveryController {
    @FXML private Label deliveryUserLabel;
    @FXML private Label bellLabel;
    @FXML private Label unreadBadgeLabel;
    @FXML private TableView<OrderRow> ordersTable;
    @FXML private TableColumn<OrderRow, Number> idColumn;
    @FXML private TableColumn<OrderRow, String> customerColumn;
    @FXML private TableColumn<OrderRow, String> routeColumn;
    @FXML private TableColumn<OrderRow, String> vehicleColumn;
    @FXML private TableColumn<OrderRow, String> placedAtColumn;
    @FXML private TableColumn<OrderRow, String> statusColumn;
    @FXML private TableColumn<OrderRow, Void> actionColumn;
    @FXML private Button simulateMovementButton;
    @FXML private Label trackingStatusLabel;
    @FXML private WebView mapWebView;

    private final OrderRepository orderRepository = new OrderRepository();
    private final NotificationRepository notificationRepository = new NotificationRepository();
    private final NotificationService notificationService = new NotificationService(notificationRepository);
    private final TrackingService trackingService = new TrackingService(
            Graph.buildDemoGraph(),
            orderRepository,
            new TrackingRepository(),
            new UserRepository()
    );

    private final ObservableList<OrderRow> rows = FXCollections.observableArrayList();

    private String deliveryUser;
    private OrderRepository.OrderRecord activeOrder;
    private JavaScriptBridge jsBridge;
    private ScheduledExecutorService pollingScheduler;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().id));
        customerColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().customer));
        routeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().route));
        vehicleColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().vehicle));
        placedAtColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().placedAt));
        statusColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));

        statusColumn.setCellFactory(col -> new TableCell<>() {
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
                    case "PLACED" -> setStyle("-fx-text-fill: #E65100; -fx-font-weight: bold;");
                    case "ACCEPTED" -> setStyle("-fx-text-fill: #1565C0; -fx-font-weight: bold;");
                    case "DELIVERED" -> setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                    default -> setStyle("");
                }
            }
        });

        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button actionButton = new Button();
            {
                actionButton.setOnAction(e -> {
                    OrderRow row = getTableView().getItems().get(getIndex());
                    if ("PLACED".equals(row.status)) {
                        acceptOrder(row.id);
                    } else if ("ACCEPTED".equals(row.status)) {
                        completeOrder(row.id);
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
                OrderRow row = getTableView().getItems().get(getIndex());
                if ("PLACED".equals(row.status)) {
                    actionButton.setText("Accept");
                    actionButton.setDisable(activeOrder != null);
                    setGraphic(actionButton);
                } else if ("ACCEPTED".equals(row.status) && deliveryUser != null && deliveryUser.equals(row.deliveryPerson)) {
                    actionButton.setText("Complete");
                    actionButton.setDisable(false);
                    setGraphic(actionButton);
                } else {
                    setGraphic(null);
                }
            }
        });

        ordersTable.setItems(rows);

        WebEngine engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent("Mozilla/5.0 JavaFX WebView");
        jsBridge = new JavaScriptBridge(engine);
        engine.loadContent(JavaScriptBridge.buildMapHtml());
    }

    public void setDeliveryUser(String username) {
        this.deliveryUser = username;
        deliveryUserLabel.setText("Delivery: " + username);
        refreshOrders();
        startPolling();
    }

    private void refreshOrders() {
        if (deliveryUser == null) {
            return;
        }
        rows.clear();
        List<OrderRepository.OrderRecord> orders = orderRepository.getOrdersForDeliveryView(deliveryUser);
        activeOrder = orderRepository.getActiveAcceptedOrder(deliveryUser);

        for (OrderRepository.OrderRecord record : orders) {
            rows.add(new OrderRow(
                    record.getId(),
                    record.getCustomerUsername(),
                    record.getSourceCity() + " -> " + record.getDestinationCity(),
                    record.getVehicleType(),
                    record.getPlacedAt(),
                    record.getStatus(),
                    record.getAssignedDeliveryUsername(),
                    record.getCustomerUsername(),
                    record.getSourceCity(),
                    record.getDestinationCity()
            ));
        }
        ordersTable.refresh();
    }

    private void acceptOrder(long orderId) {
        if (activeOrder != null) {
            showInfo("You already have an active order.");
            return;
        }

        boolean accepted = orderRepository.assignOrder(orderId, deliveryUser);
        if (!accepted) {
            showInfo("Order could not be accepted.");
            return;
        }

        OrderRepository.OrderRecord order = orderRepository.getOrderById(orderId);
        if (order != null) {
            activeOrder = order;
            trackingService.startTracking(
                    order.getId(),
                    deliveryUser,
                    order.getSourceCity(),
                    order.getDestinationCity(),
                    new TrackingService.TrackingListener() {
                        @Override
                        public void onTrackingUpdate(long id,
                                                     double lat,
                                                     double lng,
                                                     String eta,
                                                     double remainingDistanceKm,
                                                     double fraction) {
                            jsBridge.updateDeliveryMarker(lat, lng, eta);
                            trackingStatusLabel.setText(String.format("Live: %.2f km left | ETA %s", remainingDistanceKm, eta));
                        }

                        @Override
                        public void onTrackingCompleted(long id) {
                            trackingStatusLabel.setText("Tracking completed");
                        }
                    }
            );
            notificationService.notifyOrderAccepted(order.getCustomerUsername(), order.getId(), deliveryUser);
        }

        refreshOrders();
    }

    private void completeOrder(long orderId) {
        boolean completed = orderRepository.completeOrder(orderId, deliveryUser);
        if (!completed) {
            showInfo("Order completion failed.");
            return;
        }

        OrderRepository.OrderRecord order = orderRepository.getOrderById(orderId);
        if (order != null) {
            trackingService.stopTracking(orderId);
            notificationService.notifyOrderDelivered(order.getCustomerUsername(), order.getId());
        }
        activeOrder = null;
        trackingStatusLabel.setText("Order delivered and tracker stopped");
        refreshOrders();
    }

    @FXML
    private void onSimulateMovement() {
        if (activeOrder == null) {
            showInfo("No active order to simulate.");
            return;
        }

        trackingService.simulateAdvance(activeOrder.getId(), 0.1,
            new TrackingService.TrackingListener() {
                @Override
                public void onTrackingUpdate(long id,
                             double lat,
                             double lng,
                             String eta,
                             double remainingDistanceKm,
                             double fraction) {
                jsBridge.updateDeliveryMarker(lat, lng, eta);
                trackingStatusLabel.setText(String.format(
                    "Manual: %.0f%% | %.2f km left | ETA %s",
                    fraction * 100.0,
                    remainingDistanceKm,
                    eta
                ));
                }

                @Override
                public void onTrackingCompleted(long id) {
                trackingStatusLabel.setText("Manual simulation reached destination");
                }
            }
        );
    }

    private void startPolling() {
        if (pollingScheduler != null && !pollingScheduler.isShutdown()) {
            pollingScheduler.shutdownNow();
        }

        pollingScheduler = Executors.newSingleThreadScheduledExecutor();
        pollingScheduler.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            if (deliveryUser == null) {
                return;
            }
            unreadBadgeLabel.setText(String.valueOf(notificationService.countUnread(deliveryUser)));
            refreshOrders();
        }), 0, 10, TimeUnit.SECONDS);
    }

    @FXML
    private void openNotifications() {
        if (deliveryUser == null) {
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Notifications");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        ListView<String> listView = new ListView<>();
        List<NotificationRepository.NotificationRecord> records = notificationService.latest(deliveryUser, 10);
        for (NotificationRepository.NotificationRecord record : records) {
            listView.getItems().add(record.createdAt() + " - " + record.message());
        }

        ButtonType markAllType = new ButtonType("Mark all read", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(markAllType);
        dialog.getDialogPane().setContent(listView);

        dialog.setResultConverter(type -> {
            if (type == markAllType) {
                notificationService.markAllRead(deliveryUser);
                unreadBadgeLabel.setText("0");
            }
            return null;
        });

        dialog.showAndWait();
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
                            String placedAt,
                            String status,
                            String deliveryPerson,
                            String customerUser,
                            String sourceCity,
                            String destinationCity) {
    }
}
