
package smart_fence_backend.dto;

import java.time.LocalDateTime;

public class SensorReadingResponse {

    private Long id;
    private Long componentId;
    private Boolean rainDetected;
    private Integer rainLevel;
    private LocalDateTime recordedAt;

    public SensorReadingResponse(
            Long id,
            Long componentId,
            Boolean rainDetected,
            Integer rainLevel,
            LocalDateTime recordedAt) {

        this.id = id;
        this.componentId = componentId;
        this.rainDetected = rainDetected;
        this.rainLevel = rainLevel;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getComponentId() {
        return componentId;
    }

    public Boolean getRainDetected() {
        return rainDetected;
    }

    public Integer getRainLevel() {
        return rainLevel;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
