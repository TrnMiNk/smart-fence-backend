
package smart_fence_backend.dto;

import java.time.LocalDateTime;

public class DeviceResponse {

    private Long id;
    private String deviceCode;
    private String deviceName;
    private String status;
    private String systemState;
    private LocalDateTime lastSeen;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public DeviceResponse() {
    }

    public DeviceResponse(
            Long id,
            String deviceCode,
            String deviceName,
            String status,
            String systemState,
            LocalDateTime lastSeen,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        this.id = id;
        this.deviceCode = deviceCode;
        this.deviceName = deviceName;
        this.status = status;
        this.systemState = systemState;
        this.lastSeen = lastSeen;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getStatus() {
        return status;
    }

    public String getSystemState() {
        return systemState;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
