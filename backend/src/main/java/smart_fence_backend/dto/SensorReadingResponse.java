
package smart_fence_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SensorReadingResponse {

    private Long id;
    private Long componentId;
    private BigDecimal temperature;
    private BigDecimal humidity;
    private LocalDateTime recordedAt;

    public SensorReadingResponse(
            Long id,
            Long componentId,
            BigDecimal temperature,
            BigDecimal humidity,
            LocalDateTime recordedAt) {

        this.id = id;
        this.componentId = componentId;
        this.temperature = temperature;
        this.humidity = humidity;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getComponentId() {
        return componentId;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public BigDecimal getHumidity() {
        return humidity;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
