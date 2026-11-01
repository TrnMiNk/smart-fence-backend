
package smart_fence_backend.mqtt;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import smart_fence_backend.dto.SensorReadingRequest;
import smart_fence_backend.dto.DeviceCommandAckPayload;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.DeviceRepository;
import smart_fence_backend.service.DeviceCommandService;
import smart_fence_backend.service.IntrusionEventService;
import smart_fence_backend.service.SensorReadingService;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class MqttSubscriber {

    private final DeviceCommandService deviceCommandService;
    private final SensorReadingService sensorReadingService;
    private final IntrusionEventService intrusionEventService;
    private final DeviceRepository deviceRepository;
    private final DeviceComponentRepository componentRepository;
    private final JsonMapper jsonMapper;
    private final PendingSystemAckRegistry pendingSystemAckRegistry;

    public MqttSubscriber(
            DeviceCommandService deviceCommandService,
            SensorReadingService sensorReadingService,
            IntrusionEventService intrusionEventService,
            DeviceRepository deviceRepository,
            DeviceComponentRepository componentRepository,
            JsonMapper jsonMapper,
            PendingSystemAckRegistry pendingSystemAckRegistry
    ) {
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
    public ApplicationRunner subscribeToTopics(MqttService mqttService) {

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

            Pattern temperaturePattern = Pattern.compile(
                    "\"?temperature\"?\\s*:\\s*(-?\\d+(?:\\.\\d+)?)"
            );

            Pattern humidityPattern = Pattern.compile(
                    "\"?humidity\"?\\s*:\\s*(-?\\d+(?:\\.\\d+)?)"
            );

            Matcher temperatureMatcher =
                    temperaturePattern.matcher(payload);

            Matcher humidityMatcher =
                    humidityPattern.matcher(payload);

            if (!temperatureMatcher.find()
                    || !humidityMatcher.find()) {
                throw new IllegalArgumentException(
                        "Invalid telemetry payload: " + payload
                );
            }

            BigDecimal temperature = new BigDecimal(
                    temperatureMatcher.group(1)
            );

            BigDecimal humidity = new BigDecimal(
                    humidityMatcher.group(1)
            );

            Device device = findDevice(deviceCode);

            DeviceComponent dht22 = findComponent(
                    device.getId(),
                    "DHT22"
            );

            SensorReadingRequest request =
                    new SensorReadingRequest();

            request.setTemperature(temperature);
            request.setHumidity(humidity);

            var savedReading = sensorReadingService.saveReading(
                    dht22.getId(),
                    request
            );

            System.out.println(
                    "Telemetry saved: DEVICE=" + deviceCode
                            + ", COMPONENT_ID=" + dht22.getId()
                            + ", TEMPERATURE=" + temperature
                            + ", HUMIDITY=" + humidity
                            + ", READING_ID=" + savedReading.getId()
            );

        } catch (Exception e) {
            System.err.println(
                    "Failed to process telemetry: " + e.getMessage()
            );
        }
    }

    // Xu ly su kien xam nhap
    private void handleIntrusionEvent(String topic, String payload) {

        System.out.println("MQTT EVENT RAW: [" + payload + "]");

        try {
            String deviceCode = extractDeviceCode(topic, "events");

            Pattern eventTypePattern = Pattern.compile(
                    "\"?eventType\"?\\s*:\\s*\"?([A-Za-z_]+)\"?"
            );

            Pattern descriptionPattern = Pattern.compile(
                    "\"?description\"?\\s*:\\s*\"?([^\"}]*)\"?"
            );

            Matcher eventTypeMatcher =
                    eventTypePattern.matcher(payload);

            Matcher descriptionMatcher =
                    descriptionPattern.matcher(payload);

            if (!eventTypeMatcher.find()) {
                throw new IllegalArgumentException(
                        "Missing eventType in payload: " + payload
                );
            }

            String eventType = eventTypeMatcher.group(1);

            if (!"LASER_BLOCKED".equals(eventType)) {
                throw new IllegalArgumentException(
                        "Unsupported event type: " + eventType
                );
            }

            String description = descriptionMatcher.find()
                    ? descriptionMatcher.group(1).trim()
                    : "Laser beam interrupted";

            Device device = findDevice(deviceCode);

            DeviceComponent laser = findComponent(
                    device.getId(),
                    "LASER"
            );

            var savedEvent = intrusionEventService.createIntrusionEvent(
                    device.getId(),
                    laser.getId(),
                    description
            );

            System.out.println(
                    "Intrusion event saved: DEVICE=" + deviceCode
                            + ", EVENT_ID=" + savedEvent.getId()
                            + ", EVENT_TYPE=" + savedEvent.getEventType()
                            + ", STATUS=" + savedEvent.getEventStatus()
            );

        } catch (Exception e) {
            System.err.println(
                    "Failed to process intrusion event: " + e.getMessage()
            );
        }
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
