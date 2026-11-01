
package smart_fence_backend.dto;

import java.time.LocalDateTime;

public class CameraResponse {

    private Long id;
    private Long componentId;
    private String streamUrl;
    private String status;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;

    public CameraResponse(
            Long id,
            Long componentId,
            String streamUrl,
            String status,
            LocalDateTime lastSeen,
            LocalDateTime createdAt) {

        this.id = id;
        this.componentId = componentId;
        this.streamUrl = streamUrl;
        this.status = status;
        this.lastSeen = lastSeen;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getComponentId() {
        return componentId;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
