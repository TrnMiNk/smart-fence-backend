package smart_fence_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import smart_fence_backend.dto.DeviceCommandResponse;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceCommand;
import smart_fence_backend.repository.DeviceCommandRepository;
import smart_fence_backend.repository.DeviceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/** Owns the short database transactions used around system-command dispatch. */
@Service
public class SystemCommandTransactionService {

    private static final List<String> SYSTEM_COMMANDS = List.of(
            "SYSTEM_ON", "SYSTEM_OFF"
    );

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository commandRepository;

    public SystemCommandTransactionService(
            DeviceRepository deviceRepository,
            DeviceCommandRepository commandRepository) {
        this.deviceRepository = deviceRepository;
        this.commandRepository = commandRepository;
    }

    @Transactional
    public Reservation reserve(Long deviceId, String commandType) {
        Device device = deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new NoSuchElementException("Device not found!"));
        if (!"ONLINE".equals(device.getStatus())) {
            throw new IllegalArgumentException(
                    "Device must be ONLINE to receive a system command!");
        }
        if (commandRepository.existsByDevice_IdAndCommandTypeInAndCommandStatusIn(
                deviceId, SYSTEM_COMMANDS, List.of("PENDING", "SENT"))) {
            throw new IllegalArgumentException(
                    "A system command is already pending for this device!");
        }

        DeviceCommand command = new DeviceCommand();
        command.setDevice(device);
        command.setComponent(null);
        command.setCommandType(commandType);
        command.setCommandStatus("PENDING");
        DeviceCommand saved = commandRepository.saveAndFlush(command);
        return new Reservation(saved.getId(), device.getDeviceCode());
    }

    @Transactional
    public DeviceCommandResponse markSent(Long commandId) {
        LockedCommand locked = lockDeviceThenCommand(commandId);
        DeviceCommand command = locked.command();
        if (!"PENDING".equals(command.getCommandStatus())) {
            throw new IllegalStateException("System command is not PENDING!");
        }

        command.setCommandStatus("SENT");
        return toResponse(commandRepository.save(command));
    }

    @Transactional
    public DeviceCommandResponse markFailed(Long commandId) {
        LockedCommand locked = lockDeviceThenCommand(commandId);
        DeviceCommand command = locked.command();
        if ("PENDING".equals(command.getCommandStatus())
                || "SENT".equals(command.getCommandStatus())) {
            command.setCommandStatus("FAILED");
            command.setExecutedAt(LocalDateTime.now());
            commandRepository.save(command);
        }
        return toResponse(command);
    }

    @Transactional(readOnly = true)
    public DeviceCommandResponse getResponse(Long commandId) {
        DeviceCommand command = commandRepository.findById(commandId)
                .orElseThrow(() -> new NoSuchElementException("Command not found!"));
        return toResponse(command);
    }

    private LockedCommand lockDeviceThenCommand(Long commandId) {
        DeviceCommand initial = commandRepository.findById(commandId)
                .orElseThrow(() -> new NoSuchElementException("Command not found!"));
        Device device = deviceRepository.findByIdForUpdate(initial.getDevice().getId())
                .orElseThrow(() -> new NoSuchElementException("Device not found!"));
        DeviceCommand command = commandRepository.findByIdForUpdate(commandId)
                .orElseThrow(() -> new NoSuchElementException("Command not found!"));
        return new LockedCommand(device, command);
    }

    private DeviceCommandResponse toResponse(DeviceCommand command) {
        return new DeviceCommandResponse(
                command.getId(),
                command.getDevice().getId(),
                command.getComponent() == null
                        ? null : command.getComponent().getId(),
                command.getCommandType(),
                command.getCommandStatus(),
                command.getCreatedAt(),
                command.getExecutedAt());
    }

    public record Reservation(Long commandId, String deviceCode) { }

    private record LockedCommand(Device device, DeviceCommand command) { }
}
