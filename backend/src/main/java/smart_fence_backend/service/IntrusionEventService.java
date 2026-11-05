
package smart_fence_backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smart_fence_backend.dto.IntrusionEventReceipt;
import smart_fence_backend.dto.IntrusionEventRequest;
import smart_fence_backend.dto.IntrusionEventResponse;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.entity.IntrusionEvent;
import smart_fence_backend.repository.DeviceRepository;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.IntrusionEventRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Persists an MQTT event and returns only after the surrounding transaction
     * has committed. The MQTT handler may publish the returned receipt afterward.
     */
    public IntrusionEventReceipt processMqttEvent(
            String deviceCode, IntrusionEventRequest request) {
        if (request == null || request.getEventId() == null
                || request.getEventId().isBlank()) {
            return null;
        }

        ValidatedEvent event = validate(request);
        if (event == null) {
            return rejected(request.getEventId(), "INVALID_PAYLOAD");
        }

        Optional<Device> deviceResult = deviceRepository.findByDeviceCode(deviceCode);
        if (deviceResult.isEmpty()) {
            return rejected(event.eventId(), "UNKNOWN_DEVICE");
        }
        Device device = deviceResult.get();

        Optional<IntrusionEvent> existing = eventRepository
                .findByDevice_DeviceCodeAndEventId(deviceCode, event.eventId());
        if (existing.isPresent()) {
            return classifyExisting(existing.get(), event);
        }

        if (!"LASER_BLOCKED".equals(event.eventType())) {
            return rejected(event.eventId(), "INVALID_EVENT_TYPE");
        }

        DeviceComponent laser = componentRepository.findByDevice_Id(device.getId())
                .stream()
                .filter(component -> "LASER".equalsIgnoreCase(component.getComponentType()))
                .findFirst()
                .orElse(null);
        if (laser == null) {
            return rejected(event.eventId(), "UNKNOWN_LASER_COMPONENT");
        }

        IntrusionEvent intrusionEvent = new IntrusionEvent();
        intrusionEvent.setDevice(device);
        intrusionEvent.setComponent(laser);
        intrusionEvent.setEventId(event.eventId());
        intrusionEvent.setOccurredAt(event.occurredAt());
        intrusionEvent.setEventType(event.eventType());
        intrusionEvent.setEventStatus("NEW");
        intrusionEvent.setDescription(event.description());
        eventRepository.saveAndFlush(intrusionEvent);

        return receipt(event.eventId(), "SAVED", null);
    }

    /**
     * Called after a unique-key race has rolled back the insert transaction.
     * A missing row means the constraint failure was unrelated and must remain
     * an error rather than being reported as an application-level duplicate.
     */
    @Transactional(readOnly = true)
    public Optional<IntrusionEventReceipt> resolveConcurrentMqttEvent(
            String deviceCode, IntrusionEventRequest request) {
        if (request == null || request.getEventId() == null
                || request.getEventId().isBlank()) {
            return Optional.empty();
        }
        ValidatedEvent event = validate(request);
        if (event == null) return Optional.empty();

        return eventRepository.findByDevice_DeviceCodeAndEventId(
                        deviceCode, event.eventId())
                .map(existing -> classifyExisting(existing, event));
    }

    private ValidatedEvent validate(IntrusionEventRequest request) {
        String eventId = request.getEventId().trim();
        if (eventId.length() != 36) return null;
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (request.getEventType() == null || request.getEventType().isBlank()
                || request.getOccurredAt() == null || request.getOccurredAt().isBlank()) {
            return null;
        }

        LocalDateTime occurredAt;
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(request.getOccurredAt());
            if (!ZoneOffset.UTC.equals(parsed.getOffset())) return null;
            occurredAt = LocalDateTime.ofInstant(
                    parsed.toInstant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }

        String description = request.getDescription();
        if (description == null || description.isBlank()) {
            description = "Laser beam interrupted";
        }
        return new ValidatedEvent(
                eventId,
                request.getEventType().trim(),
                description.trim(),
                occurredAt);
    }

    private IntrusionEventReceipt classifyExisting(
            IntrusionEvent existing, ValidatedEvent incoming) {
        boolean sameContent = existing.getEventType().equals(incoming.eventType())
                && java.util.Objects.equals(existing.getDescription(), incoming.description())
                && java.util.Objects.equals(existing.getOccurredAt(), incoming.occurredAt());
        return sameContent
                ? receipt(incoming.eventId(), "DUPLICATE", null)
                : rejected(incoming.eventId(), "EVENT_ID_CONFLICT");
    }

    private IntrusionEventReceipt rejected(String eventId, String reason) {
        return receipt(eventId, "REJECTED", reason);
    }

    private IntrusionEventReceipt receipt(String eventId, String status, String reason) {
        return new IntrusionEventReceipt(eventId, status, reason, Instant.now().toString());
    }

    private record ValidatedEvent(
            String eventId,
            String eventType,
            String description,
            LocalDateTime occurredAt) {
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
