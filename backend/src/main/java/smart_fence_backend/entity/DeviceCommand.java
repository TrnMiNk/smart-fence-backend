
package smart_fence_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "device_commands",
    indexes = {
        @Index(name = "idx_command_created_at", columnList = "created_at"),
        @Index(name = "idx_command_status", columnList = "command_status")
    }
)
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private DeviceComponent component;

    @Column(name = "command_type", nullable = false, length = 30)
    private String commandType;

    @Column(name = "command_status", nullable = false, length = 20)
    private String commandStatus = "PENDING";

    @Column(name = "ack_system_state", length = 20)
    private String ackSystemState;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    public DeviceCommand() {
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

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public String getCommandStatus() {
        return commandStatus;
    }

    public void setCommandStatus(String commandStatus) {
        this.commandStatus = commandStatus;
    }

    public String getAckSystemState() {
        return ackSystemState;
    }

    public void setAckSystemState(String ackSystemState) {
        this.ackSystemState = ackSystemState;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
