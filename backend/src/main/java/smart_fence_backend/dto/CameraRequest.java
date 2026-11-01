
package smart_fence_backend.dto;

public class CameraRequest {

    private String streamUrl;

    public CameraRequest() {
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }
}
