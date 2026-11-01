
package smart_fence_backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import smart_fence_backend.dto.IntrusionEventResponse;
import smart_fence_backend.service.IntrusionEventService;

import java.util.List;

@RestController
@RequestMapping("/api/intrusion-events")
@CrossOrigin(origins = "*")
public class IntrusionEventController {

    private final IntrusionEventService intrusionEventService;

    public IntrusionEventController(
            IntrusionEventService intrusionEventService) {
        this.intrusionEventService = intrusionEventService;
    }

    // Lay toan bo su kien xam nhap
    @GetMapping
    public ResponseEntity<List<IntrusionEventResponse>> getAllEvents() {

        return ResponseEntity.ok(
                intrusionEventService.getAllEvents());
    }

    // Lay lich su su kien theo thiet bi
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<Page<IntrusionEventResponse>> getEventsByDevice(
            @PathVariable Long deviceId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        return ResponseEntity.ok(
                intrusionEventService.getEventsByDevice(
                        deviceId, page, size));
    }

    // Loc su kien theo trang thai
    @GetMapping("/status/{status}")
    public ResponseEntity<List<IntrusionEventResponse>> getEventsByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(
                intrusionEventService.getEventsByStatus(status));
    }

    // Ghi nhan su kien laser bi ngat
    @PostMapping("/device/{deviceId}/component/{componentId}")
    public ResponseEntity<IntrusionEventResponse> createIntrusionEvent(
            @PathVariable Long deviceId,
            @PathVariable Long componentId,
            @RequestParam(name = "description", required = false)
            String description) {

        IntrusionEventResponse savedEvent =
                intrusionEventService.createIntrusionEvent(
                        deviceId, componentId, description);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedEvent);
    }

    // Danh dau su kien da duoc xu ly
    @PatchMapping("/{eventId}/resolve")
    public ResponseEntity<IntrusionEventResponse> resolveEvent(
            @PathVariable Long eventId) {

        return ResponseEntity.ok(
                intrusionEventService.resolveEvent(eventId));
    }
}
