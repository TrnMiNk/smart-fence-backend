
package smart_fence_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import smart_fence_backend.dto.DeviceRequest;
import smart_fence_backend.dto.DeviceResponse;
import smart_fence_backend.service.DeviceService;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@CrossOrigin(origins = "*")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    // Lay tat ca thiet bi
    @GetMapping
    public ResponseEntity<List<DeviceResponse>> getAllDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    // Lay thiet bi theo ID
    @GetMapping("/{id}")
    public ResponseEntity<DeviceResponse> getDeviceById(
            @PathVariable Long id) {

        return ResponseEntity.ok(deviceService.getDeviceById(id));
    }

    // Tim thiet bi theo ma
    @GetMapping("/code/{deviceCode}")
    public ResponseEntity<DeviceResponse> getDeviceByCode(
            @PathVariable String deviceCode) {

        return ResponseEntity.ok(
                deviceService.getDeviceByCode(deviceCode));
    }

    // Tao thiet bi moi
    @PostMapping
    public ResponseEntity<DeviceResponse> createDevice(
            @RequestBody DeviceRequest request) {

        DeviceResponse savedDevice = deviceService.createDevice(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedDevice);
    }

    // Cap nhat trang thai
    @PatchMapping("/{id}/status")
    public ResponseEntity<DeviceResponse> updateDeviceStatus(
            @PathVariable Long id,
            @RequestParam(name = "status", required = true) String status) {

        return ResponseEntity.ok(
                deviceService.updateDeviceStatus(id, status));
    }

    // Xoa thiet bi
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(
            @PathVariable Long id) {

        deviceService.deleteDevice(id);

        return ResponseEntity.noContent().build();
    }
}
