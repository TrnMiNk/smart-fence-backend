
package smart_fence_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import smart_fence_backend.dto.SensorReadingRequest;
import smart_fence_backend.dto.SensorReadingResponse;
import smart_fence_backend.service.SensorReadingService;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/sensor-readings")
@CrossOrigin(origins = "*")
public class SensorReadingController {

    private final SensorReadingService sensorReadingService;

    public SensorReadingController(
            SensorReadingService sensorReadingService) {
        this.sensorReadingService = sensorReadingService;
    }

    // Lay 50 du lieu cam bien gan nhat
    @GetMapping("/component/{componentId}")
    public ResponseEntity<List<SensorReadingResponse>> getLatestReadings(
            @PathVariable Long componentId) {

        return ResponseEntity.ok(
                sensorReadingService.getLatestReadings(componentId));
    }

    // Lay du lieu cam bien moi nhat
    @GetMapping("/component/{componentId}/latest")
    public ResponseEntity<SensorReadingResponse> getLatestReading(
            @PathVariable Long componentId) {

        return sensorReadingService.getLatestReading(componentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Lay lich su du lieu trong khoang thoi gian
    @GetMapping("/component/{componentId}/history")
    public ResponseEntity<List<SensorReadingResponse>> getReadingsByTimeRange(
            @PathVariable Long componentId,
            @RequestParam(name = "start", required = true) String start,
            @RequestParam(name = "end", required = true) String end) {

        LocalDateTime startTime;
        LocalDateTime endTime;

        try {
            startTime = LocalDateTime.parse(start);
            endTime = LocalDateTime.parse(end);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Invalid datetime format! Use ISO-8601 format, "
                    + "example: 2026-10-30T08:00:00");
        }

        return ResponseEntity.ok(
                sensorReadingService.getReadingsByTimeRange(
                        componentId, startTime, endTime));
    }

    // Nhan du lieu cam bien tu ESP32
    @PostMapping("/component/{componentId}")
    public ResponseEntity<SensorReadingResponse> saveReading(
            @PathVariable Long componentId,
            @RequestBody SensorReadingRequest request) {

        SensorReadingResponse savedReading =
                sensorReadingService.saveReading(componentId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedReading);
    }
}
