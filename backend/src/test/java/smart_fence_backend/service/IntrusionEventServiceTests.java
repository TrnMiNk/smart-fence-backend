package smart_fence_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smart_fence_backend.dto.IntrusionEventReceipt;
import smart_fence_backend.dto.IntrusionEventRequest;
import smart_fence_backend.entity.Device;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.entity.IntrusionEvent;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.DeviceRepository;
import smart_fence_backend.repository.IntrusionEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class IntrusionEventServiceTests {

    private static final String DEVICE_CODE = "SF-11";
    private static final String EVENT_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String OCCURRED_AT = "2026-10-31T10:15:30.123Z";
    private static final LocalDateTime OCCURRED_AT_DB =
            LocalDateTime.of(2026, 10, 31, 10, 15, 30, 123_000_000);

    private DeviceRepository deviceRepository;
    private DeviceComponentRepository componentRepository;
    private IntrusionEventRepository eventRepository;
    private IntrusionEventService service;
    private Device device;
    private DeviceComponent laser;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        componentRepository = mock(DeviceComponentRepository.class);
        eventRepository = mock(IntrusionEventRepository.class);
        service = new IntrusionEventService(eventRepository, deviceRepository, componentRepository);

        device = new Device();
        device.setId(11L);
        device.setDeviceCode(DEVICE_CODE);
        laser = new DeviceComponent();
        laser.setDevice(device);
        laser.setComponentType("LASER");
        when(deviceRepository.findByDeviceCode(DEVICE_CODE)).thenReturn(Optional.of(device));
        when(componentRepository.findByDevice_Id(11L)).thenReturn(List.of(laser));
    }

    @Test
    void validFirstEventIsSavedWithDeviceComponentAndPayloadFields() {
        IntrusionEventRequest request = request(EVENT_ID, "LASER_BLOCKED", "Beam interrupted", OCCURRED_AT);

        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE, request);

        assertReceipt(receipt, "SAVED", null);
        var captor = org.mockito.ArgumentCaptor.forClass(IntrusionEvent.class);
        verify(eventRepository, times(1)).saveAndFlush(captor.capture());
        IntrusionEvent saved = captor.getValue();
        assertEquals(EVENT_ID, saved.getEventId());
        assertEquals("LASER_BLOCKED", saved.getEventType());
        assertEquals("Beam interrupted", saved.getDescription());
        assertEquals(OCCURRED_AT_DB, saved.getOccurredAt());
        assertEquals(device, saved.getDevice());
        assertEquals(laser, saved.getComponent());
        assertEquals("NEW", saved.getEventStatus());
    }

    @Test
    void sameEventIdAndContentReturnsDuplicateWithoutSaving() {
        when(eventRepository.findByDevice_DeviceCodeAndEventId(DEVICE_CODE, EVENT_ID))
                .thenReturn(Optional.of(existing("LASER_BLOCKED", "Beam interrupted", OCCURRED_AT_DB)));

        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Beam interrupted", OCCURRED_AT));

        assertReceipt(receipt, "DUPLICATE", null);
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameEventIdWithDifferentDescriptionReturnsConflictWithoutSaving() {
        when(eventRepository.findByDevice_DeviceCodeAndEventId(DEVICE_CODE, EVENT_ID))
                .thenReturn(Optional.of(existing("LASER_BLOCKED", "Original", OCCURRED_AT_DB)));

        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Changed", OCCURRED_AT));

        assertReceipt(receipt, "REJECTED", "EVENT_ID_CONFLICT");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameEventIdWithDifferentEventTypeReturnsConflict() {
        when(eventRepository.findByDevice_DeviceCodeAndEventId(DEVICE_CODE, EVENT_ID))
                .thenReturn(Optional.of(existing("LASER_BLOCKED", "Same", OCCURRED_AT_DB)));

        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "OTHER_EVENT", "Same", OCCURRED_AT));

        assertReceipt(receipt, "REJECTED", "EVENT_ID_CONFLICT");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void sameEventIdWithDifferentOccurredAtReturnsConflict() {
        when(eventRepository.findByDevice_DeviceCodeAndEventId(DEVICE_CODE, EVENT_ID))
                .thenReturn(Optional.of(existing("LASER_BLOCKED", "Same", OCCURRED_AT_DB)));

        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Same", "2026-10-31T10:15:31.123Z"));

        assertReceipt(receipt, "REJECTED", "EVENT_ID_CONFLICT");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidUuidReturnsInvalidPayloadWithoutDatabaseWrites() {
        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request("not-a-uuid", "LASER_BLOCKED", "Description", OCCURRED_AT));

        assertNotNull(receipt);
        assertEquals("not-a-uuid", receipt.getEventId());
        assertEquals("REJECTED", receipt.getStatus());
        assertEquals("INVALID_PAYLOAD", receipt.getReason());
        verify(eventRepository, never()).saveAndFlush(any());
        verify(deviceRepository, never()).findByDeviceCode(any());
    }

    @Test
    void missingEventTypeReturnsInvalidPayload() {
        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, null, "Description", OCCURRED_AT));

        assertReceipt(receipt, "REJECTED", "INVALID_PAYLOAD");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingOccurredAtReturnsInvalidPayload() {
        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Description", null));

        assertReceipt(receipt, "REJECTED", "INVALID_PAYLOAD");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonUtcTimestampReturnsInvalidPayload() {
        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Description", "2026-10-31T10:15:30.123+07:00"));

        assertReceipt(receipt, "REJECTED", "INVALID_PAYLOAD");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownDeviceReturnsUnknownDevice() {
        when(deviceRepository.findByDeviceCode(DEVICE_CODE)).thenReturn(Optional.empty());

        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Description", OCCURRED_AT));

        assertReceipt(receipt, "REJECTED", "UNKNOWN_DEVICE");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidEventTypeReturnsInvalidEventType() {
        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "MOTION", "Description", OCCURRED_AT));

        assertReceipt(receipt, "REJECTED", "INVALID_EVENT_TYPE");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingLaserComponentReturnsUnknownLaserComponent() {
        when(componentRepository.findByDevice_Id(11L)).thenReturn(List.of());

        IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Description", OCCURRED_AT));

        assertReceipt(receipt, "REJECTED", "UNKNOWN_LASER_COMPONENT");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void nullOrBlankDescriptionUsesDefaultForSavedEventAndDuplicateComparison() {
        for (String description : new String[] {null, "   "}) {
            IntrusionEventRequest request = request(EVENT_ID, "LASER_BLOCKED", description, OCCURRED_AT);
            IntrusionEventReceipt receipt = service.processMqttEvent(DEVICE_CODE, request);
            assertReceipt(receipt, "SAVED", null);
            var captor = org.mockito.ArgumentCaptor.forClass(IntrusionEvent.class);
            verify(eventRepository, times(description == null ? 1 : 2)).saveAndFlush(captor.capture());
            assertEquals("Laser beam interrupted", captor.getValue().getDescription());
        }

        when(eventRepository.findByDevice_DeviceCodeAndEventId(DEVICE_CODE, EVENT_ID))
                .thenReturn(Optional.of(existing("LASER_BLOCKED", "Laser beam interrupted", OCCURRED_AT_DB)));
        for (String description : new String[] {null, "   "}) {
            IntrusionEventReceipt duplicate = service.processMqttEvent(DEVICE_CODE,
                    request(EVENT_ID, "LASER_BLOCKED", description, OCCURRED_AT));
            assertReceipt(duplicate, "DUPLICATE", null);
        }
        verify(eventRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void resolveConcurrentEventReturnsDuplicateForSameContent() {
        when(eventRepository.findByDevice_DeviceCodeAndEventId(DEVICE_CODE, EVENT_ID))
                .thenReturn(Optional.of(existing("LASER_BLOCKED", "Description", OCCURRED_AT_DB)));

        Optional<IntrusionEventReceipt> result = service.resolveConcurrentMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Description", OCCURRED_AT));

        assertTrue(result.isPresent());
        assertReceipt(result.orElseThrow(), "DUPLICATE", null);
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolveConcurrentEventReturnsConflictForDifferentContent() {
        when(eventRepository.findByDevice_DeviceCodeAndEventId(DEVICE_CODE, EVENT_ID))
                .thenReturn(Optional.of(existing("LASER_BLOCKED", "Original", OCCURRED_AT_DB)));

        Optional<IntrusionEventReceipt> result = service.resolveConcurrentMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Changed", OCCURRED_AT));

        assertTrue(result.isPresent());
        assertReceipt(result.orElseThrow(), "REJECTED", "EVENT_ID_CONFLICT");
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolveConcurrentEventReturnsEmptyWhenNoRowExists() {
        when(eventRepository.findByDevice_DeviceCodeAndEventId(DEVICE_CODE, EVENT_ID))
                .thenReturn(Optional.empty());

        Optional<IntrusionEventReceipt> result = service.resolveConcurrentMqttEvent(DEVICE_CODE,
                request(EVENT_ID, "LASER_BLOCKED", "Description", OCCURRED_AT));

        assertTrue(result.isEmpty());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolveConcurrentEventReturnsEmptyForInvalidPayload() {
        Optional<IntrusionEventReceipt> result = service.resolveConcurrentMqttEvent(DEVICE_CODE,
                request("invalid", "LASER_BLOCKED", "Description", OCCURRED_AT));

        assertTrue(result.isEmpty());
        verify(eventRepository, never()).findByDevice_DeviceCodeAndEventId(any(), any());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    private static IntrusionEventRequest request(
            String eventId, String eventType, String description, String occurredAt) {
        IntrusionEventRequest request = new IntrusionEventRequest();
        request.setEventId(eventId);
        request.setEventType(eventType);
        request.setDescription(description);
        request.setOccurredAt(occurredAt);
        return request;
    }

    private IntrusionEvent existing(String eventType, String description, LocalDateTime occurredAt) {
        IntrusionEvent event = new IntrusionEvent();
        event.setDevice(device);
        event.setEventId(EVENT_ID);
        event.setEventType(eventType);
        event.setDescription(description);
        event.setOccurredAt(occurredAt);
        return event;
    }

    private static void assertReceipt(IntrusionEventReceipt receipt, String status, String reason) {
        assertNotNull(receipt);
        assertEquals(EVENT_ID, receipt.getEventId());
        assertEquals(status, receipt.getStatus());
        assertEquals(reason, receipt.getReason());
        assertNotNull(receipt.getAcknowledgedAt());
    }
}
