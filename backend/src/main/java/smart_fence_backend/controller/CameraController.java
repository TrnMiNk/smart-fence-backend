
package smart_fence_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import smart_fence_backend.dto.CameraRequest;
import smart_fence_backend.dto.CameraResponse;
import smart_fence_backend.service.CameraService;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@CrossOrigin(origins = "*")
public class CameraController {

    private final CameraService cameraService;

    public CameraController(CameraService cameraService) {
        this.cameraService = cameraService;
    }

    // Lay danh sach tat ca camera
    @GetMapping
    public ResponseEntity<List<CameraResponse>> getAllCameras() {

        return ResponseEntity.ok(
                cameraService.getAllCameras());
    }

    // Tim camera theo ID linh kien
    @GetMapping("/component/{componentId}")
    public ResponseEntity<CameraResponse> getCameraByComponent(
            @PathVariable Long componentId) {

        return cameraService.getCameraByComponent(componentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Loc camera theo trang thai
    @GetMapping("/status/{status}")
    public ResponseEntity<List<CameraResponse>> getCamerasByStatus(
            @PathVariable String status) {

        return ResponseEntity.ok(
                cameraService.getCamerasByStatus(status));
    }

    // Dang ky camera moi
    @PostMapping("/component/{componentId}")
    public ResponseEntity<CameraResponse> createCamera(
            @PathVariable Long componentId,
            @RequestBody CameraRequest request) {

        CameraResponse savedCamera =
                cameraService.createCamera(componentId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedCamera);
    }

    // Cap nhat URL livestream
    @PatchMapping("/{cameraId}/stream-url")
    public ResponseEntity<CameraResponse> updateStreamUrl(
            @PathVariable Long cameraId,
            @RequestParam(name = "streamUrl", required = true)
            String streamUrl) {

        return ResponseEntity.ok(
                cameraService.updateStreamUrl(cameraId, streamUrl));
    }

    // Cap nhat trang thai camera
    @PatchMapping("/{cameraId}/status")
    public ResponseEntity<CameraResponse> updateCameraStatus(
            @PathVariable Long cameraId,
            @RequestParam(name = "status", required = true)
            String status) {

        return ResponseEntity.ok(
                cameraService.updateCameraStatus(cameraId, status));
    }
}
