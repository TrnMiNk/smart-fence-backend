
package smart_fence_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cameras")
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false, unique = true)
    private DeviceComponent component;

    @Column(name = "stream_url", length = 500)
    private String streamUrl;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OFFLINE";

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Camera() {
    }

    public Long getId() {
        return id;
    }

    public DeviceComponent getComponent() {
        return component;
    }

    public void setComponent(DeviceComponent component) {
        this.component = component;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(LocalDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
