
package smart_fence_backend.service;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import smart_fence_backend.dto.DeviceCommandResponse;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.entity.DeviceCommand;

import smart_fence_backend.mqtt.MqttService;
import smart_fence_backend.mqtt.PendingSystemAckRegistry;
import smart_fence_backend.repository.DeviceRepository;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.DeviceCommandRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@Transactional
public class DeviceCommandService {

    private final DeviceCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceComponentRepository componentRepository;
    private final MqttService mqttService;
    private final SystemCommandTransactionService systemCommandTransactions;
    private final PendingSystemAckRegistry pendingSystemAckRegistry;

    private static final List<String> VALID_STATUSES = List.of(
            "PENDING", "SENT", "SUCCESS", "FAILED"
    );

    private static final List<String> VALID_COMMANDS = List.of(
            "BUZZER_ON",
            "BUZZER_OFF",
            "BUZZER_AUTO",
            "BUZZER_MANUAL"
    );

    private static final List<String> SYSTEM_COMMANDS = List.of(
            "SYSTEM_ON", "SYSTEM_OFF"
    );

    public DeviceCommandService(
            DeviceCommandRepository commandRepository,
            DeviceRepository deviceRepository,
            DeviceComponentRepository componentRepository,
            MqttService mqttService,
            SystemCommandTransactionService systemCommandTransactions,
            PendingSystemAckRegistry pendingSystemAckRegistry) {

        this.commandRepository = commandRepository;
        this.deviceRepository = deviceRepository;
        this.componentRepository = componentRepository;
        this.mqttService = mqttService;
        this.systemCommandTransactions = systemCommandTransactions;
        this.pendingSystemAckRegistry = pendingSystemAckRegistry;
    }

    // Chuyen Entity sang DTO
    private DeviceCommandResponse convertToResponse(DeviceCommand command) {

        return new DeviceCommandResponse(
                command.getId(),
                command.getDevice().getId(),
                command.getComponent() == null
                        ? null : command.getComponent().getId(),
                command.getCommandType(),
                command.getCommandStatus(),
                command.getCreatedAt(),
                command.getExecutedAt()
        );
    }

    // Lay lich su lenh dieu khien theo thiet bi
    @Transactional(readOnly = true)
    public List<DeviceCommandResponse> getCommandsByDevice(Long deviceId) {

        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new NoSuchElementException("Device not found!");
        }

        return commandRepository
                .findByDevice_IdOrderByCreatedAtDesc(deviceId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Lay lenh theo trang thai
    @Transactional(readOnly = true)
    public List<DeviceCommandResponse> getCommandsByStatus(String status) {

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Command status is required!");
        }

        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Invalid command status!");
        }

        return commandRepository
                .findByCommandStatus(normalizedStatus)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Tao lenh dieu khien coi va gui qua MQTT
    public DeviceCommandResponse createBuzzerCommand(
            Long deviceId, Long componentId, String commandType) {

        if (deviceId == null) {
            throw new IllegalArgumentException("Device ID is required!");
        }

        if (componentId == null) {
            throw new IllegalArgumentException("Component ID is required!");
        }

        if (commandType == null || commandType.isBlank()) {
            throw new IllegalArgumentException("Buzzer command is required!");
        }

        String normalizedCommand = commandType.trim()
                .toUpperCase(Locale.ROOT);

        if (!VALID_COMMANDS.contains(normalizedCommand)) {
            throw new IllegalArgumentException("Invalid buzzer command!");
        }

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() ->
                        new NoSuchElementException("Device not found!"));

        DeviceComponent component = componentRepository.findById(componentId)
                .orElseThrow(() ->
                        new NoSuchElementException("Component not found!"));

        if (!component.getDevice().getId().equals(deviceId)) {
            throw new IllegalArgumentException(
                    "Component does not belong to this device!");
        }

        if (!"BUZZER".equalsIgnoreCase(component.getComponentType())) {
            throw new IllegalArgumentException(
                    "Component is not a BUZZER!");
        }

        // Tao lenh trong MySQL
        DeviceCommand command = new DeviceCommand();

        command.setDevice(device);
        command.setComponent(component);
        command.setCommandType(normalizedCommand);
        command.setCommandStatus("PENDING");

        DeviceCommand savedCommand = commandRepository.save(command);

        // Tao MQTT Topic
        String topic = "smart-fence/"
                + device.getDeviceCode()
                + "/commands";

        // Tao JSON payload
        String payload = String.format(
                "{\"commandId\":%d,\"command\":\"%s\"}",
                savedCommand.getId(),
                normalizedCommand
        );

        // Gui lenh qua MQTT
        try {
            mqttService.publish(topic, payload);

            savedCommand.setCommandStatus("SENT");

            System.out.println(
                    "Buzzer command sent to device: "
                            + device.getDeviceCode()
            );

        } catch (MqttException ex) {

            savedCommand.setCommandStatus("FAILED");
            savedCommand.setExecutedAt(LocalDateTime.now());

            System.err.println(
                    "Failed to publish MQTT command: " + ex.getMessage()
            );
        }

        DeviceCommand updatedCommand =
                commandRepository.save(savedCommand);

        return convertToResponse(updatedCommand);
    }

    // Create and publish a whole-device system command.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DeviceCommandResponse createSystemCommand(
            Long deviceId, String commandType) {

        if (deviceId == null) {
            throw new IllegalArgumentException("Device ID is required!");
        }
        if (commandType == null || commandType.isBlank()) {
            throw new IllegalArgumentException("System command is required!");
        }

        String normalizedCommand = commandType.trim().toUpperCase(Locale.ROOT);
        if (!SYSTEM_COMMANDS.contains(normalizedCommand)) {
            throw new IllegalArgumentException("Invalid system command!");
        }

        SystemCommandTransactionService.Reservation reservation =
                systemCommandTransactions.reserve(deviceId, normalizedCommand);

        String topic = "smart-fence/" + reservation.deviceCode() + "/commands";
        String payload = String.format(
                "{\"commandId\":%d,\"command\":\"%s\"}",
                reservation.commandId(), normalizedCommand);
        pendingSystemAckRegistry.beginDispatch(reservation.commandId());
        boolean publishSucceeded = false;
        try {
            mqttService.publish(topic, payload);
            publishSucceeded = true;
        } catch (MqttException | RuntimeException ex) {
            System.err.println("Failed to publish system command: " + ex.getMessage());
        }

        // Reservation committed PENDING before publish. Commit SENT before releasing
        // any ACKs buffered while the synchronous MQTT publish was in progress.
        try {
            systemCommandTransactions.markSent(reservation.commandId());
        } catch (RuntimeException markSentFailure) {
            // A failed commit can be transient. Retry a bounded number of times while
            // the dispatch gate retains all ACKs; never process an ACK against PENDING.
            RuntimeException lastFailure = markSentFailure;
            boolean sent = false;
            for (int retry = 0; retry < 2; retry++) {
                try {
                    systemCommandTransactions.markSent(reservation.commandId());
                    sent = true;
                    break;
                } catch (RuntimeException retryFailure) {
                    lastFailure.addSuppressed(retryFailure);
                }
            }
            if (sent) {
                pendingSystemAckRegistry.completeDispatch(reservation.commandId());
                return systemCommandTransactions.getResponse(reservation.commandId());
            }
            try {
                PendingSystemAckRegistry.AbandonResult result =
                        pendingSystemAckRegistry.abandonDispatch(
                                reservation.commandId(),
                                () -> systemCommandTransactions.markFailed(reservation.commandId()));
                if (!result.abandoned()) {
                    // Keep PENDING and its valid ACK together. A later DB-backed
                    // reconciliation can retry markSent; never invalidate/drop SUCCESS.
                    System.err.println("System command remains PENDING with buffered SUCCESS "
                            + "after SENT retries; ACK retained: ID=" + reservation.commandId());
                }
            } catch (RuntimeException recoveryFailure) {
                lastFailure.addSuppressed(recoveryFailure);
                // Keep dispatch and ACKs in RAM. DB PENDING remains recoverable by an
                // operator/reconciliation process; no false device state is applied.
            }
            throw lastFailure;
        }
        // The proxied @Transactional method returned, so SENT has committed.
        pendingSystemAckRegistry.completeDispatch(reservation.commandId());
        if (!publishSucceeded) {
            return systemCommandTransactions.markFailed(reservation.commandId());
        }
        return systemCommandTransactions.getResponse(reservation.commandId());
    }

    // Xu ly ACK MQTT va xac thuc thiet bi gui phan hoi
    public DeviceCommandResponse processMqttAck(
            Long commandId,
            String deviceCode,
            String status) {

        return processMqttAck(commandId, deviceCode, null, status, null);
    }

    public DeviceCommandResponse processMqttAck(
            Long commandId,
            String deviceCode,
            String commandType,
            String status,
            String systemState) {

        if (commandId == null) {
            throw new IllegalArgumentException(
                    "Command ID is required!");
        }

        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Device code is required!");
        }

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Command status is required!");
        }

        String normalizedStatus = status.trim()
                .toUpperCase(Locale.ROOT);

        // Chi chap nhan SUCCESS hoac FAILED
        if (!normalizedStatus.equals("SUCCESS")
                && !normalizedStatus.equals("FAILED")) {

            throw new IllegalArgumentException(
                    "Invalid MQTT ACK status!");
        }

        DeviceCommand initialCommand = commandRepository.findById(commandId)
                .orElseThrow(() ->
                        new NoSuchElementException("Command not found!"));

        // Keep lock order consistent with command creation: Device, then command.
        Device device = deviceRepository.findByIdForUpdate(
                        initialCommand.getDevice().getId())
                .orElseThrow(() -> new NoSuchElementException("Device not found!"));
        DeviceCommand command = commandRepository.findByIdForUpdate(commandId)
                .orElseThrow(() -> new NoSuchElementException("Command not found!"));

        // Kiem tra thiet bi gui ACK co dung khong
        String actualDeviceCode = command.getDevice().getDeviceCode();

        if (!actualDeviceCode.equals(deviceCode)) {
            throw new IllegalArgumentException(
                    "ACK device does not match command device!");
        }

        boolean systemCommand = SYSTEM_COMMANDS.contains(command.getCommandType());
        String normalizedType = commandType == null
                ? null : commandType.trim().toUpperCase(Locale.ROOT);
        String normalizedSystemState = systemState == null
                ? null : systemState.trim().toUpperCase(Locale.ROOT);
        if (systemCommand) {
            if (!command.getCommandType().equals(normalizedType)) {
                throw new IllegalArgumentException("ACK command type does not match command!");
            }
            if (normalizedSystemState == null
                    || !List.of("ON", "OFF", "UNKNOWN").contains(normalizedSystemState)) {
                throw new IllegalArgumentException("Invalid or missing ACK systemState!");
            }
            if ("SUCCESS".equals(normalizedStatus)
                    && !(("SYSTEM_ON".equals(command.getCommandType()) && "ON".equals(normalizedSystemState))
                    || ("SYSTEM_OFF".equals(command.getCommandType()) && "OFF".equals(normalizedSystemState)))) {
                throw new IllegalArgumentException("ACK systemState does not match command target!");
            }
        } else if (normalizedType != null
                && !command.getCommandType().equals(normalizedType)) {
            throw new IllegalArgumentException("ACK command type does not match command!");
        }

        String currentStatus = command.getCommandStatus();

        // Xu ly ACK trung lap
        if ("SUCCESS".equals(currentStatus)
                || "FAILED".equals(currentStatus)) {

            if (currentStatus.equals(normalizedStatus)) {
                if (systemCommand && !normalizedSystemState.equals(
                        command.getAckSystemState())) {
                    throw new IllegalArgumentException(
                            "Duplicate ACK conflicts with completed command!");
                }
                return convertToResponse(command);
            }

            throw new IllegalArgumentException(
                    "Command already completed with another status!");
        }

        // Chi chap nhan ACK cho lenh da gui
        if (!"SENT".equals(currentStatus)) {
            throw new IllegalArgumentException(
                    "Command has not been sent!");
        }

        command.setCommandStatus(normalizedStatus);
        command.setExecutedAt(LocalDateTime.now());
        if (systemCommand) {
            command.setAckSystemState(normalizedSystemState);
        }

        if (systemCommand && "SUCCESS".equals(normalizedStatus)
                && "ONLINE".equals(device.getStatus())) {
            device.setSystemState(normalizedSystemState);
            deviceRepository.save(device);
        }

        DeviceCommand savedCommand = commandRepository.save(command);

        System.out.println(
                "Verified MQTT ACK: device="
                        + deviceCode
                        + ", commandId="
                        + commandId
                        + ", status="
                        + normalizedStatus
        );

        return convertToResponse(savedCommand);
    }

    // Cap nhat trang thai thuc thi lenh qua API
    public DeviceCommandResponse updateCommandStatus(
            Long commandId, String status) {

        if (commandId == null) {
            throw new IllegalArgumentException(
                    "Command ID is required!");
        }

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Command status is required!");
        }

        String normalizedStatus = status.trim()
                .toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Invalid command status!");
        }

        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() ->
                        new NoSuchElementException("Command not found!"));

        if (SYSTEM_COMMANDS.contains(command.getCommandType())) {
            throw new IllegalArgumentException(
                    "System command status can only be completed by a valid MQTT ACK!");
        }

        String currentStatus = command.getCommandStatus();

        // Khong cho phep thay doi lenh da ket thuc
        if ("SUCCESS".equals(currentStatus)
                || "FAILED".equals(currentStatus)) {

            if (!currentStatus.equals(normalizedStatus)) {
                throw new IllegalArgumentException(
                        "Cannot change a completed command!");
            }

            return convertToResponse(command);
        }

        command.setCommandStatus(normalizedStatus);

        // Ghi nhan thoi diem hoan thanh
        if ("SUCCESS".equals(normalizedStatus)
                || "FAILED".equals(normalizedStatus)) {
            command.setExecutedAt(LocalDateTime.now());
        }

        DeviceCommand savedCommand = commandRepository.save(command);

        return convertToResponse(savedCommand);
    }
}
