
package smart_fence_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import smart_fence_backend.dto.DeviceCommandResponse;
import smart_fence_backend.dto.DeviceSystemCommandRequest;
import smart_fence_backend.service.DeviceCommandService;

import java.util.List;

@RestController
@RequestMapping("/api/device-commands")
@CrossOrigin(origins = "*")
public class DeviceCommandController {

    private final DeviceCommandService deviceCommandService;

    public DeviceCommandController(
            DeviceCommandService deviceCommandService) {
        this.deviceCommandService = deviceCommandService;
    }

    // Lay lich su lenh dieu khien theo thiet bi
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<List<DeviceCommandResponse>> getCommandsByDevice(
            @PathVariable Long deviceId) {

        return ResponseEntity.ok(
                deviceCommandService.getCommandsByDevice(deviceId));
    }

    // Lay danh sach lenh theo trang thai
    @GetMapping("/status/{status}")
    public ResponseEntity<List<DeviceCommandResponse>> getCommandsByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(
                deviceCommandService.getCommandsByStatus(status));
    }

    // Tao lenh dieu khien coi bao dong
    @PostMapping("/device/{deviceId}/component/{componentId}")
    public ResponseEntity<DeviceCommandResponse> createBuzzerCommand(
            @PathVariable Long deviceId,
            @PathVariable Long componentId,
            @RequestParam(name = "commandType", required = true)
            String commandType) {

        DeviceCommandResponse savedCommand =
                deviceCommandService.createBuzzerCommand(
                        deviceId,
                        componentId,
                        commandType);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedCommand);
    }

    @PostMapping("/device/{deviceId}/system")
    public ResponseEntity<DeviceCommandResponse> createSystemCommand(
            @PathVariable Long deviceId,
            @RequestBody DeviceSystemCommandRequest request) {

        DeviceCommandResponse savedCommand = deviceCommandService.createSystemCommand(
                deviceId, request == null ? null : request.getCommandType());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCommand);
    }

    // Cap nhat trang thai thuc thi lenh
    @PatchMapping("/{commandId}/status")
    public ResponseEntity<DeviceCommandResponse> updateCommandStatus(
            @PathVariable Long commandId,
            @RequestParam(name = "status", required = true)
            String status) {

        return ResponseEntity.ok(
                deviceCommandService.updateCommandStatus(
                        commandId, status));
    }
}
