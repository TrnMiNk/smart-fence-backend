
package smart_fence_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import smart_fence_backend.dto.DeviceComponentRequest;
import smart_fence_backend.dto.DeviceComponentResponse;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.repository.DeviceRepository;
import smart_fence_backend.repository.DeviceComponentRepository;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@Transactional
public class DeviceComponentService {

    private final DeviceComponentRepository componentRepository;
    private final DeviceRepository deviceRepository;

    private static final List<String> VALID_TYPES = List.of(
            "DHT22", "LASER", "BUZZER", "CAMERA"
    );

    private static final List<String> VALID_STATUSES = List.of(
            "ACTIVE", "INACTIVE"
    );

    public DeviceComponentService(
            DeviceComponentRepository componentRepository,
            DeviceRepository deviceRepository) {

        this.componentRepository = componentRepository;
        this.deviceRepository = deviceRepository;
    }

    // Chuyen Entity sang Response DTO
    private DeviceComponentResponse convertToResponse(
            DeviceComponent component) {

        return new DeviceComponentResponse(
                component.getId(),
                component.getDevice().getId(),
                component.getComponentCode(),
                component.getComponentName(),
                component.getComponentType(),
                component.getStatus(),
                component.getCreatedAt()
        );
    }

    // Lay tat ca linh kien
    @Transactional(readOnly = true)
    public List<DeviceComponentResponse> getAllComponents() {

        return componentRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Lay linh kien theo Device ID
    @Transactional(readOnly = true)
    public List<DeviceComponentResponse> getComponentsByDevice(
            Long deviceId) {

        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new NoSuchElementException("Device not found!");
        }

        return componentRepository.findByDevice_Id(deviceId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Lay linh kien theo loai
    @Transactional(readOnly = true)
    public List<DeviceComponentResponse> getComponentsByType(
            String componentType) {

        if (componentType == null || componentType.isBlank()) {
            throw new IllegalArgumentException(
                    "Component type is required!");
        }

        String type = componentType.trim().toUpperCase(Locale.ROOT);

        if (!VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Invalid component type!");
        }

        return componentRepository.findByComponentType(type)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Tao linh kien moi
    public DeviceComponentResponse createComponent(
            Long deviceId,
            DeviceComponentRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request cannot be null!");
        }

        if (deviceId == null) {
            throw new IllegalArgumentException(
                    "Device ID is required!");
        }

        if (request.getComponentCode() == null
                || request.getComponentCode().isBlank()) {
            throw new IllegalArgumentException(
                    "Component code is required!");
        }

        if (request.getComponentName() == null
                || request.getComponentName().isBlank()) {
            throw new IllegalArgumentException(
                    "Component name is required!");
        }

        if (request.getComponentType() == null
                || request.getComponentType().isBlank()) {
            throw new IllegalArgumentException(
                    "Component type is required!");
        }

        String componentCode = request.getComponentCode().trim();
        String componentName = request.getComponentName().trim();
        String type = request.getComponentType()
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Invalid component type!");
        }

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() ->
                        new NoSuchElementException("Device not found!"));

        if (componentRepository.findByDevice_IdAndComponentCode(
                deviceId, componentCode).isPresent()) {

            throw new IllegalArgumentException(
                    "Component code already exists for this device!");
        }

        DeviceComponent component = new DeviceComponent();

        component.setDevice(device);
        component.setComponentCode(componentCode);
        component.setComponentName(componentName);
        component.setComponentType(type);
        component.setStatus("ACTIVE");

        DeviceComponent savedComponent =
                componentRepository.save(component);

        return convertToResponse(savedComponent);
    }

    // Cap nhat trang thai linh kien
    public DeviceComponentResponse updateComponentStatus(
            Long componentId,
            String status) {

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Component status is required!");
        }

        String newStatus = status.trim().toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid component status!");
        }

        DeviceComponent component =
                componentRepository.findById(componentId)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                        "Component not found!"));

        component.setStatus(newStatus);

        DeviceComponent updatedComponent =
                componentRepository.save(component);

        return convertToResponse(updatedComponent);
    }

    // Xoa linh kien
    public void deleteComponent(Long componentId) {

        DeviceComponent component =
                componentRepository.findById(componentId)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                        "Component not found!"));

        componentRepository.delete(component);
    }
}
