package com.deliveryroute.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.deliveryroute.algorithm.Graph;
import com.deliveryroute.database.OrderRepository;
import com.deliveryroute.database.TrackingRepository;
import com.deliveryroute.database.UserRepository;
import com.deliveryroute.model.City;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * Service that simulates delivery movement and pushes location updates.
 */
public class TrackingService {
    private static final double AVG_SPEED_KMH = 40.0;

    private final Graph graph;
    private final OrderRepository orderRepository;
    private final TrackingRepository trackingRepository;
    private final UserRepository userRepository;
    private final ScheduledExecutorService scheduler;
    private final Map<Long, TrackingSession> sessions;
    private final SimpleDoubleProperty liveFraction;

    public TrackingService(Graph graph,
                           OrderRepository orderRepository,
                           TrackingRepository trackingRepository,
                           UserRepository userRepository) {
        this.graph = graph;
        this.orderRepository = orderRepository;
        this.trackingRepository = trackingRepository;
        this.userRepository = userRepository;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.sessions = new ConcurrentHashMap<>();
        this.liveFraction = new SimpleDoubleProperty(0.0);
    }

    public SimpleDoubleProperty liveFractionProperty() {
        return liveFraction;
    }

    public boolean startTracking(long orderId,
                                 String deliveryUser,
                                 String sourceCity,
                                 String destinationCity,
                                 TrackingListener listener) {
        City src = findCityByName(sourceCity);
        City dest = findCityByName(destinationCity);
        if (src == null || dest == null) {
            return false;
        }

        double totalDistanceKm = haversineKm(src.getLatitude(), src.getLongitude(), dest.getLatitude(), dest.getLongitude());
        int estimatedMinutes = (int) Math.max(1, Math.round(totalDistanceKm / AVG_SPEED_KMH * 60.0));
        orderRepository.updateEstimatedMinutes(orderId, estimatedMinutes);

        stopTracking(orderId);

        TrackingSession session = new TrackingSession(
                orderId,
                deliveryUser,
                src,
                dest,
                totalDistanceKm,
                estimatedMinutes,
                System.currentTimeMillis(),
                listener
        );

        Runnable task = () -> emitTick(session, false);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(task, 0, 5, TimeUnit.SECONDS);
        session.future = future;
        sessions.put(orderId, session);
        return true;
    }

    public void simulateAdvance(long orderId, double step, TrackingListener listenerOverride) {
        TrackingSession session = sessions.get(orderId);
        if (session == null) {
            return;
        }
        session.manualFraction = Math.min(1.0, session.manualFraction + Math.max(0.0, step));
        if (listenerOverride != null) {
            session.listener = listenerOverride;
        }
        emitTick(session, true);
    }

    private void emitTick(TrackingSession session, boolean manualMode) {
        double fraction;
        if (manualMode) {
            fraction = session.manualFraction;
        } else {
            double elapsedSeconds = (System.currentTimeMillis() - session.startedAtMillis) / 1000.0;
            double totalSeconds = session.estimatedMinutes * 60.0;
            fraction = totalSeconds <= 0 ? 1.0 : Math.min(1.0, elapsedSeconds / totalSeconds);
        }

        liveFraction.set(fraction);

        double interpolatedLat = session.source.getLatitude() + fraction * (session.destination.getLatitude() - session.source.getLatitude());
        double interpolatedLng = session.source.getLongitude() + fraction * (session.destination.getLongitude() - session.source.getLongitude());

        trackingRepository.addUpdate(session.orderId, session.deliveryUser, interpolatedLat, interpolatedLng);
        userRepository.updateDeliveryLocation(session.deliveryUser, interpolatedLat, interpolatedLng);

        long remainingSeconds = Math.max(0L, Math.round(session.estimatedMinutes * 60.0 * (1.0 - fraction)));
        String eta = LocalDateTime.now().plusSeconds(remainingSeconds).format(DateTimeFormatter.ofPattern("HH:mm"));
        double remainingDistanceKm = session.totalDistanceKm * (1.0 - fraction);

        if (session.listener != null) {
            Platform.runLater(() -> session.listener.onTrackingUpdate(
                    session.orderId,
                    interpolatedLat,
                    interpolatedLng,
                    eta,
                    remainingDistanceKm,
                    fraction
            ));
        }

        if (fraction >= 1.0) {
            stopTracking(session.orderId);
            if (session.listener != null) {
                Platform.runLater(() -> session.listener.onTrackingCompleted(session.orderId));
            }
        }
    }

    public void stopTracking(long orderId) {
        TrackingSession session = sessions.remove(orderId);
        if (session != null && session.future != null) {
            session.future.cancel(true);
        }
    }

    public void shutdown() {
        sessions.keySet().forEach(this::stopTracking);
        scheduler.shutdownNow();
    }

    private City findCityByName(String cityName) {
        return graph.getCities()
                .stream()
                .filter(city -> city.getName().equalsIgnoreCase(cityName))
                .findFirst()
                .orElse(null);
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

    public interface TrackingListener {
        void onTrackingUpdate(long orderId,
                              double lat,
                              double lng,
                              String eta,
                              double remainingDistanceKm,
                              double fraction);

        void onTrackingCompleted(long orderId);
    }

    private static class TrackingSession {
        private final long orderId;
        private final String deliveryUser;
        private final City source;
        private final City destination;
        private final double totalDistanceKm;
        private final int estimatedMinutes;
        private final long startedAtMillis;
        private volatile double manualFraction;
        private volatile ScheduledFuture<?> future;
        private volatile TrackingListener listener;

        private TrackingSession(long orderId,
                                String deliveryUser,
                                City source,
                                City destination,
                                double totalDistanceKm,
                                int estimatedMinutes,
                                long startedAtMillis,
                                TrackingListener listener) {
            this.orderId = orderId;
            this.deliveryUser = deliveryUser;
            this.source = source;
            this.destination = destination;
            this.totalDistanceKm = totalDistanceKm;
            this.estimatedMinutes = estimatedMinutes;
            this.startedAtMillis = startedAtMillis;
            this.listener = listener;
            this.manualFraction = 0.0;
        }
    }
}
