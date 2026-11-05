
package smart_fence_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_readings")
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    private DeviceComponent component;

    @Column(name = "rain_detected")
    private Boolean rainDetected;

    @Column(name = "rain_level")
    private Integer rainLevel;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public SensorReading() {
    }

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
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

    public Boolean getRainDetected() {
        return rainDetected;
    }

    public void setRainDetected(Boolean rainDetected) {
        this.rainDetected = rainDetected;
    }

    public Integer getRainLevel() {
        return rainLevel;
    }

    public void setRainLevel(Integer rainLevel) {
        this.rainLevel = rainLevel;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
