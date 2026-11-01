package smart_fence_backend.dto;

public class DeviceSystemCommandRequest {

    private String commandType;

    public DeviceSystemCommandRequest() {
    }

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }
}
