
package smart_fence_backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import smart_fence_backend.dto.IntrusionEventResponse;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.entity.IntrusionEvent;
import smart_fence_backend.repository.DeviceRepository;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.IntrusionEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class IntrusionEventService {

    private final IntrusionEventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceComponentRepository componentRepository;

    public IntrusionEventService(
            IntrusionEventRepository eventRepository,
            DeviceRepository deviceRepository,
            DeviceComponentRepository componentRepository) {

        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.componentRepository = componentRepository;
    }

    // Chuyển Entity sang DTO
    private IntrusionEventResponse convertToResponse(IntrusionEvent event) {

        return new IntrusionEventResponse(
                event.getId(),
                event.getDevice().getId(),
                event.getComponent() != null
                        ? event.getComponent().getId() : null,
                event.getEventType(),
                event.getEventStatus(),
                event.getDescription(),
                event.getDetectedAt(),
                event.getClosedAt()
        );
    }

    // Lấy toàn bộ sự kiện xâm nhập
    @Transactional(readOnly = true)
    public List<IntrusionEventResponse> getAllEvents() {

        return eventRepository.findAll(
                        Sort.by(Sort.Direction.DESC, "detectedAt"))
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Lấy lịch sử sự kiện theo thiết bị, có phân trang
    @Transactional(readOnly = true)
    public Page<IntrusionEventResponse> getEventsByDevice(
            Long deviceId, int page, int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination!");
        }

        if (!deviceRepository.existsById(deviceId)) {
            throw new NoSuchElementException("Device not found!");
        }

        return eventRepository.findByDevice_IdOrderByDetectedAtDesc(
                        deviceId, PageRequest.of(page, size))
                .map(this::convertToResponse);
    }

    // Lọc sự kiện theo trạng thái
    @Transactional(readOnly = true)
    public List<IntrusionEventResponse> getEventsByStatus(String status) {

        return eventRepository
                .findByEventStatusOrderByDetectedAtDesc(status)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Ghi nhận sự kiện laser bị ngắt
    public IntrusionEventResponse createIntrusionEvent(
            Long deviceId, Long componentId, String description) {

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Device not found!"));

        DeviceComponent component = componentRepository.findById(componentId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Laser component not found!"));

        if (!component.getDevice().getId().equals(deviceId)) {
            throw new IllegalArgumentException(
                    "Component does not belong to this device!");
        }

        if (!"LASER".equalsIgnoreCase(component.getComponentType())) {
            throw new IllegalArgumentException(
                    "Component is not a LASER!");
        }

        IntrusionEvent event = new IntrusionEvent();

        event.setDevice(device);
        event.setComponent(component);
        event.setEventType("LASER_BLOCKED");
        event.setEventStatus("NEW");
        event.setDescription(description);

        IntrusionEvent savedEvent = eventRepository.save(event);

        return convertToResponse(savedEvent);
    }

    // Đánh dấu sự kiện đã được xử lý
    public IntrusionEventResponse resolveEvent(Long eventId) {

        IntrusionEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Intrusion event not found!"));

        event.setEventStatus("RESOLVED");
        event.setClosedAt(LocalDateTime.now());

        IntrusionEvent savedEvent = eventRepository.save(event);

        return convertToResponse(savedEvent);
    }
}
