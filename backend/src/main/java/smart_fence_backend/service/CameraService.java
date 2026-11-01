
package smart_fence_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import smart_fence_backend.dto.CameraRequest;
import smart_fence_backend.dto.CameraResponse;
import smart_fence_backend.entity.Camera;
import smart_fence_backend.entity.DeviceComponent;

import smart_fence_backend.repository.CameraRepository;
import smart_fence_backend.repository.DeviceComponentRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class CameraService {

    private final CameraRepository cameraRepository;
    private final DeviceComponentRepository componentRepository;

    private static final List<String> VALID_STATUSES = List.of(
            "ONLINE", "OFFLINE"
    );

    private static final int MAX_URL_LENGTH = 500;

    public CameraService(
            CameraRepository cameraRepository,
            DeviceComponentRepository componentRepository) {

        this.cameraRepository = cameraRepository;
        this.componentRepository = componentRepository;
    }

    // Chuyen Entity sang DTO
    private CameraResponse convertToResponse(Camera camera) {

        return new CameraResponse(
                camera.getId(),
                camera.getComponent().getId(),
                camera.getStreamUrl(),
                camera.getStatus(),
                camera.getLastSeen(),
                camera.getCreatedAt()
        );
    }

    // Kiem tra va chuan hoa URL
    private String validateStreamUrl(String streamUrl) {

        if (streamUrl == null || streamUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Stream URL cannot be empty!");
        }

        String normalizedUrl = streamUrl.trim();

        if (normalizedUrl.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(
                    "Stream URL cannot exceed 500 characters!");
        }

        return normalizedUrl;
    }

    // Lay danh sach tat ca camera
    @Transactional(readOnly = true)
    public List<CameraResponse> getAllCameras() {

        return cameraRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Tim camera theo ID linh kien
    @Transactional(readOnly = true)
    public Optional<CameraResponse> getCameraByComponent(Long componentId) {

        if (componentId == null
                || !componentRepository.existsById(componentId)) {
            throw new NoSuchElementException(
                    "Camera component not found!");
        }

        return cameraRepository.findByComponent_Id(componentId)
                .map(this::convertToResponse);
    }

    // Loc camera theo trang thai
    @Transactional(readOnly = true)
    public List<CameraResponse> getCamerasByStatus(String status) {

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Camera status is required!");
        }

        String normalizedStatus = status.trim()
                .toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Invalid camera status!");
        }

        return cameraRepository.findByStatus(normalizedStatus)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Dang ky camera moi
    public CameraResponse createCamera(
            Long componentId,
            CameraRequest request) {

        if (componentId == null) {
            throw new IllegalArgumentException(
                    "Component ID is required!");
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request cannot be null!");
        }

        String streamUrl = validateStreamUrl(request.getStreamUrl());

        DeviceComponent component = componentRepository.findById(componentId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Camera component not found!"));

        if (!"CAMERA".equalsIgnoreCase(component.getComponentType())) {
            throw new IllegalArgumentException(
                    "Component is not a CAMERA!");
        }

        if (cameraRepository.findByComponent_Id(componentId).isPresent()) {
            throw new IllegalArgumentException(
                    "Camera already exists for this component!");
        }

        Camera camera = new Camera();
        camera.setComponent(component);
        camera.setStreamUrl(streamUrl);
        camera.setStatus("OFFLINE");

        Camera savedCamera = cameraRepository.save(camera);

        return convertToResponse(savedCamera);
    }

    // Cap nhat trang thai camera
    public CameraResponse updateCameraStatus(
            Long cameraId, String status) {

        if (cameraId == null) {
            throw new IllegalArgumentException(
                    "Camera ID is required!");
        }

        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Camera status is required!");
        }

        String normalizedStatus = status.trim()
                .toUpperCase(Locale.ROOT);

        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "Invalid camera status!");
        }

        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() ->
                        new NoSuchElementException("Camera not found!"));

        camera.setStatus(normalizedStatus);

        if ("ONLINE".equals(normalizedStatus)) {
            camera.setLastSeen(LocalDateTime.now());
        }

        Camera savedCamera = cameraRepository.save(camera);

        return convertToResponse(savedCamera);
    }

    // Cap nhat URL livestream
    public CameraResponse updateStreamUrl(
            Long cameraId, String streamUrl) {

        if (cameraId == null) {
            throw new IllegalArgumentException(
                    "Camera ID is required!");
        }

        String normalizedUrl = validateStreamUrl(streamUrl);

        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() ->
                        new NoSuchElementException("Camera not found!"));

        camera.setStreamUrl(normalizedUrl);

        Camera savedCamera = cameraRepository.save(camera);

        return convertToResponse(savedCamera);
    }
}
