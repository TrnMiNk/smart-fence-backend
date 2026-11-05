
package smart_fence_backend.mqtt;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;

import smart_fence_backend.dto.SensorReadingRequest;
import smart_fence_backend.dto.DeviceCommandAckPayload;
import smart_fence_backend.dto.IntrusionEventReceipt;
import smart_fence_backend.dto.IntrusionEventRequest;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.DeviceRepository;
import smart_fence_backend.service.DeviceCommandService;
import smart_fence_backend.service.IntrusionEventService;
import smart_fence_backend.service.SensorReadingService;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Configuration
public class MqttSubscriber {

    private final MqttService mqttService;
    private final DeviceCommandService deviceCommandService;
    private final SensorReadingService sensorReadingService;
    private final IntrusionEventService intrusionEventService;
    private final DeviceRepository deviceRepository;
    private final DeviceComponentRepository componentRepository;
    private final JsonMapper jsonMapper;
    private final PendingSystemAckRegistry pendingSystemAckRegistry;

    public MqttSubscriber(
            MqttService mqttService,
            DeviceCommandService deviceCommandService,
            SensorReadingService sensorReadingService,
            IntrusionEventService intrusionEventService,
            DeviceRepository deviceRepository,
            DeviceComponentRepository componentRepository,
            JsonMapper jsonMapper,
            PendingSystemAckRegistry pendingSystemAckRegistry
    ) {
        this.mqttService = mqttService;
        this.deviceCommandService = deviceCommandService;
        this.sensorReadingService = sensorReadingService;
        this.intrusionEventService = intrusionEventService;
        this.deviceRepository = deviceRepository;
        this.componentRepository = componentRepository;
        this.jsonMapper = jsonMapper;
        this.pendingSystemAckRegistry = pendingSystemAckRegistry;
        this.pendingSystemAckRegistry.setAckProcessor(
                (deviceCode, ack) -> processAckWithRetry(ack, deviceCode, 0));
    }

    @Bean
    @ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner subscribeToTopics() {

        return args -> {

            mqttService.subscribe(
                    "smart-fence/+/telemetry",
                    this::handleTelemetry
            );

            mqttService.subscribe(
                    "smart-fence/+/events",
                    this::handleIntrusionEvent
            );

            mqttService.subscribe(
                    "smart-fence/+/command-status",
                    this::handleCommandAck
            );
        };
    }

    // Xu ly Telemetry
    private void handleTelemetry(String topic, String payload) {

        try {
            String deviceCode = extractDeviceCode(topic, "telemetry");
            SensorReadingRequest request = parseTelemetry(payload);

            Device device = findDevice(deviceCode);

            DeviceComponent rainSensor = findComponent(
                    device.getId(),
                    "RAIN_SENSOR"
            );

            var savedReading = sensorReadingService.saveReading(
                    rainSensor.getId(),
                    request
            );

            System.out.println(
                    "Telemetry saved: DEVICE=" + deviceCode
                            + ", COMPONENT_ID=" + rainSensor.getId()
                            + ", RAIN_DETECTED=" + request.getRainDetected()
                            + ", RAIN_LEVEL=" + request.getRainLevel()
                            + ", READING_ID=" + savedReading.getId()
            );

        } catch (Exception e) {
            System.err.println(
                    "Failed to process telemetry: " + e.getMessage()
            );
        }
    }

    private SensorReadingRequest parseTelemetry(String payload) throws Exception {
        JsonNode root = jsonMapper.readTree(payload);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Telemetry must be a JSON object");
        }

        JsonNode rainDetected = root.get("rainDetected");
        JsonNode rainLevel = root.get("rainLevel");
        if (rainDetected == null || !rainDetected.isBoolean()) {
            throw new IllegalArgumentException("rainDetected must be a boolean");
        }
        if (rainLevel == null || !rainLevel.isIntegralNumber() || !rainLevel.canConvertToInt()) {
            throw new IllegalArgumentException("rainLevel must be an integer ADC value");
        }
        int adcValue = rainLevel.intValue();
        if (adcValue < 0 || adcValue > 4095) {
            throw new IllegalArgumentException("rainLevel must be between 0 and 4095");
        }

        SensorReadingRequest request = new SensorReadingRequest();
        request.setRainDetected(rainDetected.booleanValue());
        request.setRainLevel(adcValue);
        return request;
    }

    // Xu ly su kien xam nhap
    private void handleIntrusionEvent(String topic, String payload) {

        System.out.println("MQTT EVENT RAW: [" + payload + "]");

        String deviceCode;
        try {
            deviceCode = extractDeviceCode(topic, "events");
        } catch (Exception e) {
            System.err.println("Failed to process intrusion event: " + e.getMessage());
            return;
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(payload);
        } catch (Exception e) {
            // Malformed JSON has no trustworthy eventId to correlate a receipt with.
            System.err.println("Malformed intrusion event JSON; no receipt sent: " + e.getMessage());
            return;
        }

        JsonNode eventIdNode = root != null && root.isObject() ? root.get("eventId") : null;
        if (eventIdNode == null || !eventIdNode.isString()
                || eventIdNode.asString().isBlank()) {
            System.err.println("Intrusion event has no readable eventId; no receipt sent");
            return;
        }

        IntrusionEventRequest request = new IntrusionEventRequest();
        request.setEventId(eventIdNode.asString());
        request.setEventType(readString(root, "eventType"));
        request.setDescription(readString(root, "description"));
        request.setOccurredAt(readString(root, "occurredAt"));

        IntrusionEventReceipt receipt;
        try {
            receipt = intrusionEventService.processMqttEvent(deviceCode, request);
        } catch (DataIntegrityViolationException e) {
            // A concurrent insert may have won the unique-key race. Resolve it
            // only after the failed insert transaction has rolled back.
            receipt = intrusionEventService.resolveConcurrentMqttEvent(deviceCode, request)
                    .orElse(null);
            if (receipt == null) {
                System.err.println("Failed to persist intrusion event: " + e.getMessage());
                return;
            }
        } catch (Exception e) {
            // No receipt on persistence/commit failure; ESP32 retries the same eventId.
            System.err.println("Failed to persist intrusion event; no receipt sent: " + e.getMessage());
            return;
        }

        if (receipt == null) return;
        try {
            Map<String, Object> receiptPayload = new LinkedHashMap<>();
            receiptPayload.put("eventId", receipt.getEventId());
            receiptPayload.put("status", receipt.getStatus());
            if (receipt.getReason() != null) {
                receiptPayload.put("reason", receipt.getReason());
            }
            receiptPayload.put("acknowledgedAt", receipt.getAcknowledgedAt());
            mqttService.publish(
                    "smart-fence/" + deviceCode + "/event-receipts",
                    jsonMapper.writeValueAsString(receiptPayload));
            System.out.println("Intrusion event receipt sent: DEVICE=" + deviceCode
                    + ", EVENT_ID=" + receipt.getEventId()
                    + ", STATUS=" + receipt.getStatus());
        } catch (Exception e) {
            // The event transaction has already committed. A later retry becomes
            // DUPLICATE and causes another receipt attempt.
            System.err.println("Failed to publish intrusion event receipt: " + e.getMessage());
        }
    }

    private String readString(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        return value != null && value.isString() ? value.asString() : null;
    }

    // Xu ly ACK
    private void handleCommandAck(String topic, String payload) {

        try {
            String deviceCode = extractDeviceCode(
                    topic,
                    "command-status"
            );

            DeviceCommandAckPayload ack = jsonMapper.readValue(
                    payload, DeviceCommandAckPayload.class);
            if (ack.getCommandId() == null || ack.getStatus() == null) {
                throw new IllegalArgumentException(
                        "Invalid ACK payload: " + payload
                );
            }

            boolean systemAck = ack.getCommandType() != null
                    && List.of("SYSTEM_ON", "SYSTEM_OFF").contains(
                            ack.getCommandType().trim().toUpperCase(java.util.Locale.ROOT));
            PendingSystemAckRegistry.DeferResult deferResult = systemAck
                    ? pendingSystemAckRegistry.deferIfDispatching(
                            ack.getCommandId(), deviceCode, ack)
                    : PendingSystemAckRegistry.DeferResult.NO_DISPATCH;
            if (deferResult == PendingSystemAckRegistry.DeferResult.DEFERRED) {
                System.out.println("System command ACK deferred until SENT is committed: ID="
                        + ack.getCommandId());
            } else if (deferResult == PendingSystemAckRegistry.DeferResult.DISPATCH_FULL) {
                System.err.println("System command ACK retained for device retransmission because dispatch buffer is full: ID="
                        + ack.getCommandId());
                return;
            } else {
                processAckWithRetry(ack, deviceCode, 0);
            }

        } catch (Exception e) {
            System.err.println(
                    "Failed to process command ACK: " + e.getMessage()
            );
        }
    }

    private void processAckWithRetry(
            DeviceCommandAckPayload ack, String deviceCode, int attempt) {
        try {
            deviceCommandService.processMqttAck(
                    ack.getCommandId(), deviceCode, ack.getCommandType(),
                    ack.getStatus(), ack.getSystemState());
            System.out.println(
                    "Command ACK processed: ID=" + ack.getCommandId()
                            + ", DEVICE=" + deviceCode
                            + ", STATUS=" + ack.getStatus());
        } catch (RuntimeException e) {
            boolean commandNotVisibleYet = e instanceof NoSuchElementException
                    && "Command not found!".equals(e.getMessage());
            boolean commandNotSentYet = e instanceof IllegalArgumentException
                    && "Command has not been sent!".equals(e.getMessage());
            boolean systemAck = ack.getCommandType() != null
                    && List.of("SYSTEM_ON", "SYSTEM_OFF").contains(
                            ack.getCommandType().trim().toUpperCase(java.util.Locale.ROOT));
            if (!systemAck && attempt < 5
                    && (commandNotVisibleYet || commandNotSentYet)) {
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                        .execute(() -> processAckWithRetry(
                                ack, deviceCode, attempt + 1));
                return;
            }
            System.err.println("Failed to process command ACK: " + e.getMessage());
        }
    }

    // Tim thiet bi
    private Device findDevice(String deviceCode) {

        return deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Device not found: " + deviceCode
                        )
                );
    }

    // Tim linh kien theo loai
    private DeviceComponent findComponent(
            Long deviceId,
            String componentType
    ) {

        return componentRepository.findByDevice_Id(deviceId)
                .stream()
                .filter(component ->
                        componentType.equalsIgnoreCase(
                                component.getComponentType()
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                componentType + " component not found!"
                        )
                );
    }

    // Lay deviceCode tu MQTT topic
    private String extractDeviceCode(
            String topic,
            String expectedTopicType
    ) {

        String[] parts = topic.split("/");

        if (parts.length != 3
                || !"smart-fence".equals(parts[0])
                || !expectedTopicType.equals(parts[2])
                || parts[1].isBlank()) {

            throw new IllegalArgumentException(
                    "Invalid MQTT topic: " + topic
            );
        }

        return parts[1];
    }
}
