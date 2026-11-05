package smart_fence_backend.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Service
public class MqttService {
    private static final Logger logger = LoggerFactory.getLogger(MqttService.class);
    private static final long MAX_RETRY_DELAY_MS = 30_000;

    private final MqttClient mqttClient;
    private final ObjectProvider<MqttConnectOptions> connectOptionsProvider;
    private final boolean mqttEnabled;
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "mqtt-connection-manager");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running = true;
    private long nextConnectionAttemptAt;
    private long connectionRetryDelayMs = 1_000;
    private boolean wasConnected;

    public MqttService(
            ObjectProvider<MqttClient> mqttClientProvider,
            ObjectProvider<MqttConnectOptions> connectOptionsProvider,
            @Value("${mqtt.enabled:true}") boolean mqttEnabled,
            Environment environment) {
        // The test profile must remain isolated even if production MQTT variables are present.
        this.mqttEnabled = mqttEnabled && !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("test"));
        this.mqttClient = this.mqttEnabled ? mqttClientProvider.getObject() : null;
        this.connectOptionsProvider = connectOptionsProvider;
        if (this.mqttClient != null) this.mqttClient.setCallback(new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect, String serverURI) {
                logger.info("MQTT connected to {}", serverURI);
            }
            @Override public void connectionLost(Throwable cause) {
                logger.warn("MQTT connection lost; the connection manager will retry", cause);
            }
            @Override public void messageArrived(String topic, MqttMessage message) { }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
        });
    }

    @PostConstruct
    public void startConnectionManager() {
        if (!mqttEnabled) {
            logger.info("MQTT is disabled for the active profile");
            return;
        }
        // One single-threaded worker owns connect and subscribe calls; Paho auto-reconnect is off.
        worker.scheduleWithFixedDelay(this::maintainConnectionAndSubscriptions, 0, 250, TimeUnit.MILLISECONDS);
    }

    private void maintainConnectionAndSubscriptions() {
        if (!running) return;
        long now = System.currentTimeMillis();
        if (!mqttClient.isConnected()) {
            if (wasConnected) {
                wasConnected = false;
                connectionRetryDelayMs = 1_000;
                nextConnectionAttemptAt = now;
                subscriptions.values().forEach(Subscription::markPending);
            }
            if (now < nextConnectionAttemptAt) return;
            try {
                mqttClient.connect(connectOptions());
                wasConnected = true;
                connectionRetryDelayMs = 1_000;
                nextConnectionAttemptAt = 0;
                subscriptions.values().forEach(Subscription::markPending);
                logger.info("MQTT connection established");
            } catch (MqttException e) {
                if (!running || Thread.currentThread().isInterrupted()) return;
                logger.warn("MQTT connection attempt failed; retrying in {} ms: {}",
                        connectionRetryDelayMs, e.getMessage());
                nextConnectionAttemptAt = now + connectionRetryDelayMs;
                connectionRetryDelayMs = Math.min(connectionRetryDelayMs * 2, MAX_RETRY_DELAY_MS);
                return;
            }
        }
        if (mqttClient.isConnected()) retryPendingSubscriptions();
    }

    private void retryPendingSubscriptions() {
        long now = System.currentTimeMillis();
        subscriptions.forEach((topic, subscription) -> {
            if (subscription.isSubscribed() || now < subscription.nextAttemptAt()) return;
            if (!mqttClient.isConnected() || !running) return;
            try {
                mqttClient.subscribe(topic, (receivedTopic, message) ->
                        subscription.handler().accept(receivedTopic,
                                new String(message.getPayload(), StandardCharsets.UTF_8)));
                subscription.markSubscribed();
                logger.info("MQTT subscribed to {}", topic);
            } catch (MqttException e) {
                long delay = subscription.recordFailure();
                logger.warn("MQTT subscribe to {} failed; retrying in {} ms: {}",
                        topic, delay, e.getMessage());
            }
        });
    }

    @PreDestroy
    public void disconnectMqttClient() {
        running = false;
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(12, TimeUnit.SECONDS)) {
                logger.warn("MQTT connection worker did not stop within 12 seconds; client close will interrupt pending work");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (mqttClient != null) {
                if (mqttClient.isConnected()) mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (MqttException e) {
            logger.warn("Failed to close MQTT client during shutdown", e);
        }
    }

    public void publish(String topic, String payload) throws MqttException {
        if (mqttClient == null) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(false);
        mqttClient.publish(topic, message);
        logger.debug("MQTT published to {}", topic);
    }

    public void subscribe(String topic) throws MqttException {
        subscribe(topic, (receivedTopic, payload) ->
                logger.info("MQTT received on {}: {}", receivedTopic, payload));
    }

    public void subscribe(String topic, BiConsumer<String, String> handler) throws MqttException {
        if (!mqttEnabled || !running) return;
        Subscription newSubscription = new Subscription(handler);
        Subscription existing = subscriptions.putIfAbsent(topic, newSubscription);
        if (existing != null && existing.handler() != handler) {
            logger.warn("Ignoring duplicate MQTT subscription registration for {}", topic);
        }
    }

    private MqttConnectOptions connectOptions() {
        // Only resolved when the enabled connection manager starts.
        return connectOptionsProvider.getObject();
    }

    private static final class Subscription {
        private final BiConsumer<String, String> handler;
        private volatile boolean subscribed;
        private volatile long nextAttemptAt;
        private int failures;

        private Subscription(BiConsumer<String, String> handler) { this.handler = handler; }
        private BiConsumer<String, String> handler() { return handler; }
        private boolean isSubscribed() { return subscribed; }
        private long nextAttemptAt() { return nextAttemptAt; }
        private void markPending() { subscribed = false; nextAttemptAt = 0; failures = 0; }
        private void markSubscribed() { subscribed = true; failures = 0; nextAttemptAt = 0; }
        private long recordFailure() {
            long delay = Math.min(1_000L << Math.min(failures, 5), MAX_RETRY_DELAY_MS);
            failures++;
            nextAttemptAt = System.currentTimeMillis() + delay;
            return delay;
        }
    }
}
