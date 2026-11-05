
package smart_fence_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "intrusion_events",
    indexes = {
        @Index(name = "idx_intrusion_detected_at", columnList = "detected_at"),
        @Index(name = "idx_intrusion_status", columnList = "event_status")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_intrusion_device_event_id",
            columnNames = {"device_id", "event_id"}
        )
    }
)
public class IntrusionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private DeviceComponent component;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "occurred_at", columnDefinition = "DATETIME(3)")
    private LocalDateTime occurredAt;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "event_status", nullable = false, length = 20)
    private String eventStatus = "NEW";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "detected_at", insertable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public IntrusionEvent() {
    }

    public Long getId() {
        return id;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public DeviceComponent getComponent() {
        return component;
    }

    public void setComponent(DeviceComponent component) {
        this.component = component;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
