
package smart_fence_backend.dto;

import java.time.LocalDateTime;

public class DeviceCommandResponse {

    private Long id;
    private Long deviceId;
    private Long componentId;
    private String commandType;
    private String commandStatus;
    private LocalDateTime createdAt;
    private LocalDateTime executedAt;

    public DeviceCommandResponse(
            Long id,
            Long deviceId,
            Long componentId,
            String commandType,
            String commandStatus,
            LocalDateTime createdAt,
            LocalDateTime executedAt) {

        this.id = id;
        this.deviceId = deviceId;
        this.componentId = componentId;
        this.commandType = commandType;
        this.commandStatus = commandStatus;
        this.createdAt = createdAt;
        this.executedAt = executedAt;
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

    public String getCommandType() {
        return commandType;
    }

    public String getCommandStatus() {
        return commandStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }
}
