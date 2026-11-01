
package smart_fence_backend.dto;

import java.time.LocalDateTime;

public class DeviceComponentResponse {

    private Long id;
    private Long deviceId;
    private String componentCode;
    private String componentName;
    private String componentType;
    private String status;
    private LocalDateTime createdAt;

    public DeviceComponentResponse() {
    }

    public DeviceComponentResponse(
            Long id,
            Long deviceId,
            String componentCode,
            String componentName,
            String componentType,
            String status,
            LocalDateTime createdAt) {

        this.id = id;
        this.deviceId = deviceId;
        this.componentCode = componentCode;
        this.componentName = componentName;
        this.componentType = componentType;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public String getComponentName() {
        return componentName;
    }

    public String getComponentType() {
        return componentType;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
