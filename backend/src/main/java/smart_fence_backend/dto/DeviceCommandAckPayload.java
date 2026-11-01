package smart_fence_backend.dto;

public class DeviceCommandAckPayload {
    private Long commandId;
    private String commandType;
    private String status;
    private String systemState;

    public Long getCommandId() { return commandId; }
    public void setCommandId(Long commandId) { this.commandId = commandId; }
    public String getCommandType() { return commandType; }
    public void setCommandType(String commandType) { this.commandType = commandType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSystemState() { return systemState; }
    public void setSystemState(String systemState) { this.systemState = systemState; }
}
