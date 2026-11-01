
package smart_fence_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import smart_fence_backend.dto.DeviceRequest;
import smart_fence_backend.dto.DeviceResponse;
import smart_fence_backend.entity.Device;
import smart_fence_backend.repository.DeviceRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@Transactional
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    private DeviceResponse convertToResponse(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getDeviceCode(),
                device.getDeviceName(),
                device.getStatus(),
                device.getSystemState(),
                device.getLastSeen(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevices() {
        return deviceRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("Device not found!"));

        return convertToResponse(device);
    }

    @Transactional(readOnly = true)
    public DeviceResponse getDeviceByCode(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException("Device code is required!");
        }

        Device device = deviceRepository.findByDeviceCode(deviceCode.trim())
                .orElseThrow(() ->
                        new NoSuchElementException("Device not found!"));

        return convertToResponse(device);
    }

    public DeviceResponse createDevice(DeviceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null!");
        }

        if (request.getDeviceCode() == null
                || request.getDeviceCode().isBlank()) {
            throw new IllegalArgumentException("Device code is required!");
        }

        if (request.getDeviceName() == null
                || request.getDeviceName().isBlank()) {
            throw new IllegalArgumentException("Device name is required!");
        }

        String deviceCode = request.getDeviceCode().trim();
        String deviceName = request.getDeviceName().trim();

        if (deviceRepository.existsByDeviceCode(deviceCode)) {
            throw new IllegalArgumentException("Device code already exists!");
        }

        Device device = new Device();
        device.setDeviceCode(deviceCode);
        device.setDeviceName(deviceName);
        device.setStatus("OFFLINE");
        device.setSystemState("UNKNOWN");

        Device savedDevice = deviceRepository.save(device);

        return convertToResponse(savedDevice);
    }

    public DeviceResponse updateDeviceStatus(Long id, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Invalid device status!");
        }

        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);

        if (!List.of("ONLINE", "OFFLINE").contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid device status!");
        }

        Device device = deviceRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new NoSuchElementException("Device not found!"));

        String previousStatus = device.getStatus();
        if (!normalizedStatus.equals(previousStatus)) {
            device.setSystemState("UNKNOWN");
        }

        device.setStatus(normalizedStatus);

        if ("ONLINE".equals(normalizedStatus)) {
            device.setLastSeen(LocalDateTime.now());
        }

        Device updatedDevice = deviceRepository.save(device);

        return convertToResponse(updatedDevice);
    }

    public void deleteDevice(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() ->
                        new NoSuchElementException("Device not found!"));

        deviceRepository.delete(device);
    }
}
