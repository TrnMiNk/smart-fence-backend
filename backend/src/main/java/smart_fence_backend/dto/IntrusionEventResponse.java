
package smart_fence_backend.dto;

import java.time.LocalDateTime;

public class IntrusionEventResponse {

    private Long id;
    private Long deviceId;
    private Long componentId;
    private String eventType;
    private String eventStatus;
    private String description;
    private LocalDateTime detectedAt;
    private LocalDateTime closedAt;

    public IntrusionEventResponse(
            Long id,
            Long deviceId,
            Long componentId,
            String eventType,
            String eventStatus,
            String description,
            LocalDateTime detectedAt,
            LocalDateTime closedAt) {

        this.id = id;
        this.deviceId = deviceId;
        this.componentId = componentId;
        this.eventType = eventType;
        this.eventStatus = eventStatus;
        this.description = description;
        this.detectedAt = detectedAt;
        this.closedAt = closedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public Long getComponentId() {
        return componentId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }
}
