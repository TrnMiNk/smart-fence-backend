
package smart_fence_backend.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

@Service
public class MqttService {

    private static final Logger logger = LoggerFactory.getLogger(MqttService.class);

    private final MqttClient mqttClient;

    public MqttService(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @PreDestroy
    public void disconnectMqttClient() {
        if (!mqttClient.isConnected()) {
            return;
        }

        try {
            mqttClient.disconnect();
        } catch (MqttException e) {
            logger.warn("Failed to disconnect MQTT client during shutdown", e);
        }
    }

    // Gui tin nhan MQTT
    public void publish(String topic, String payload)
            throws MqttException {

        MqttMessage message = new MqttMessage(
                payload.getBytes(StandardCharsets.UTF_8)
        );

        message.setQos(1);
        message.setRetained(false);

        mqttClient.publish(topic, message);

        System.out.println(
                "MQTT Published: " + topic + " -> " + payload
        );
    }

    // Dang ky topic va nhan du lieu
    public void subscribe(String topic) throws MqttException {

        subscribe(topic, (receivedTopic, payload) ->
                System.out.println(
                        "MQTT Received: "
                                + receivedTopic
                                + " -> "
                                + payload
                )
        );
    }

    // Dang ky topic voi ham xu ly rieng
    public void subscribe(
            String topic,
            BiConsumer<String, String> messageHandler
    ) throws MqttException {

        mqttClient.subscribe(topic, (receivedTopic, message) -> {

            String payload = new String(
                    message.getPayload(),
                    StandardCharsets.UTF_8
            );

            messageHandler.accept(receivedTopic, payload);
        });

        System.out.println("MQTT Subscribed: " + topic);
    }
}
