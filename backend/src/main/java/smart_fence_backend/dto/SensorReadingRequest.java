
package smart_fence_backend.dto;

import java.math.BigDecimal;

public class SensorReadingRequest {

    private BigDecimal temperature;
    private BigDecimal humidity;

    public SensorReadingRequest() {
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getHumidity() {
        return humidity;
    }

    public void setHumidity(BigDecimal humidity) {
        this.humidity = humidity;
    }
}
