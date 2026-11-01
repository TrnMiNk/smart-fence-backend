
package smart_fence_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import smart_fence_backend.dto.DeviceComponentRequest;
import smart_fence_backend.dto.DeviceComponentResponse;
import smart_fence_backend.service.DeviceComponentService;

import java.util.List;

@RestController
@RequestMapping("/api/components")
@CrossOrigin(origins = "*")
public class DeviceComponentController {

    private final DeviceComponentService componentService;

    public DeviceComponentController(
            DeviceComponentService componentService) {
        this.componentService = componentService;
    }

    // Lay tat ca linh kien
    @GetMapping
    public ResponseEntity<List<DeviceComponentResponse>> getAllComponents() {
        return ResponseEntity.ok(
                componentService.getAllComponents());
    }

    // Lay linh kien theo Device ID
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<List<DeviceComponentResponse>> getComponentsByDevice(
            @PathVariable Long deviceId) {

        return ResponseEntity.ok(
                componentService.getComponentsByDevice(deviceId));
    }

    // Lay linh kien theo loai
    @GetMapping("/type/{componentType}")
    public ResponseEntity<List<DeviceComponentResponse>> getComponentsByType(
            @PathVariable String componentType) {

        return ResponseEntity.ok(
                componentService.getComponentsByType(componentType));
    }

    // Tao linh kien moi
    @PostMapping("/device/{deviceId}")
    public ResponseEntity<DeviceComponentResponse> createComponent(
            @PathVariable Long deviceId,
            @RequestBody DeviceComponentRequest request) {

        DeviceComponentResponse savedComponent =
                componentService.createComponent(deviceId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedComponent);
    }

    // Cap nhat trang thai linh kien
    @PatchMapping("/{componentId}/status")
    public ResponseEntity<DeviceComponentResponse> updateComponentStatus(
            @PathVariable Long componentId,
            @RequestParam(name = "status", required = true) String status) {

        return ResponseEntity.ok(
                componentService.updateComponentStatus(
                        componentId, status));
    }

    // Xoa linh kien
    @DeleteMapping("/{componentId}")
    public ResponseEntity<Void> deleteComponent(
            @PathVariable Long componentId) {

        componentService.deleteComponent(componentId);

        return ResponseEntity.noContent().build();
    }
}
