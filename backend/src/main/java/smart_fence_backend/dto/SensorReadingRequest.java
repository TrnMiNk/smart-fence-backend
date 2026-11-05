
package smart_fence_backend.dto;

public class SensorReadingRequest {

    private Boolean rainDetected;
    private Integer rainLevel;

    public SensorReadingRequest() {
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
}
