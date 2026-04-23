package com.deliveryroute.service;

import java.util.List;

import com.deliveryroute.database.NotificationRepository;

/**
 * Service for notification creation and read-state workflows.
 */
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void notifyOrderPlaced(String customerUser, long orderId) {
        notificationRepository.addNotification(customerUser, "Order #" + orderId + " placed successfully.");
    }

    public void notifyOrderAccepted(String customerUser, long orderId, String deliveryUser) {
        notificationRepository.addNotification(
                customerUser,
                "Your order #" + orderId + " has been accepted by " + deliveryUser + "."
        );
    }

    public void notifyOrderDelivered(String customerUser, long orderId) {
        notificationRepository.addNotification(
                customerUser,
                "Your order #" + orderId + " has been delivered! Please rate your experience."
        );
    }

    public long countUnread(String targetUser) {
        return notificationRepository.countUnread(targetUser);
    }

    public List<NotificationRepository.NotificationRecord> latest(String targetUser, int limit) {
        return notificationRepository.getLatestNotifications(targetUser, limit);
    }

    public boolean markAllRead(String targetUser) {
        return notificationRepository.markAllRead(targetUser);
    }
}
