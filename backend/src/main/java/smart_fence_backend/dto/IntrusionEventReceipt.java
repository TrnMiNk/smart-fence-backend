package smart_fence_backend.dto;

public class IntrusionEventReceipt {

    private final String eventId;
    private final String status;
    private final String reason;
    private final String acknowledgedAt;

    public IntrusionEventReceipt(String eventId, String status, String reason,
            String acknowledgedAt) {
        this.eventId = eventId;
        this.status = status;
        this.reason = reason;
        this.acknowledgedAt = acknowledgedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getAcknowledgedAt() {
        return acknowledgedAt;
    }
}
