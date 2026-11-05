
package smart_fence_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import smart_fence_backend.dto.SensorReadingRequest;
import smart_fence_backend.dto.SensorReadingResponse;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.entity.SensorReading;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.SensorReadingRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class SensorReadingService {

    private final SensorReadingRepository readingRepository;
    private final DeviceComponentRepository componentRepository;

    public SensorReadingService(
            SensorReadingRepository readingRepository,
            DeviceComponentRepository componentRepository) {

        this.readingRepository = readingRepository;
        this.componentRepository = componentRepository;
    }

    // Chuyen Entity sang DTO
    private SensorReadingResponse convertToResponse(SensorReading reading) {

        return new SensorReadingResponse(
                reading.getId(),
                reading.getComponent().getId(),
                reading.getRainDetected(),
                reading.getRainLevel(),
                reading.getRecordedAt()
        );
    }

    // Validate component existence and type.
    private DeviceComponent getRainSensorComponent(Long componentId) {

        if (componentId == null) {
            throw new IllegalArgumentException(
                    "Component ID is required!");
        }

        DeviceComponent component = componentRepository.findById(componentId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Sensor component not found!"));

        if (!"RAIN_SENSOR".equalsIgnoreCase(component.getComponentType())) {
            throw new IllegalArgumentException(
                    "Component is not a rain sensor!");
        }

        return component;
    }

    // Lay 50 du lieu cam bien gan nhat
    @Transactional(readOnly = true)
    public List<SensorReadingResponse> getLatestReadings(Long componentId) {

        getRainSensorComponent(componentId);

        return readingRepository
                .findTop50ByComponent_IdOrderByRecordedAtDesc(componentId)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Lay du lieu cam bien moi nhat
    @Transactional(readOnly = true)
    public Optional<SensorReadingResponse> getLatestReading(Long componentId) {

        getRainSensorComponent(componentId);

        return readingRepository
                .findTop1ByComponent_IdOrderByRecordedAtDesc(componentId)
                .map(this::convertToResponse);
    }

    // Lay du lieu trong khoang thoi gian
    @Transactional(readOnly = true)
    public List<SensorReadingResponse> getReadingsByTimeRange(
            Long componentId,
            LocalDateTime start,
            LocalDateTime end) {

        getRainSensorComponent(componentId);

        if (start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("Invalid time range!");
        }

        return readingRepository
                .findByComponent_IdAndRecordedAtBetweenOrderByRecordedAtAsc(
                        componentId, start, end)
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Luu du lieu cam bien moi
    public SensorReadingResponse saveReading(
            Long componentId,
            SensorReadingRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request cannot be null!");
        }

        DeviceComponent component = getRainSensorComponent(componentId);
        Boolean rainDetected = request.getRainDetected();
        Integer rainLevel = request.getRainLevel();

        if (rainDetected == null) {
            throw new IllegalArgumentException("rainDetected is required!");
        }
        if (rainLevel == null || rainLevel < 0 || rainLevel > 4095) {
            throw new IllegalArgumentException(
                    "rainLevel must be between 0 and 4095!");
        }

        SensorReading reading = new SensorReading();
        reading.setComponent(component);
        reading.setRainDetected(rainDetected);
        reading.setRainLevel(rainLevel);

        SensorReading savedReading =
                readingRepository.saveAndFlush(reading);

        SensorReading refreshedReading = readingRepository
                .findById(savedReading.getId())
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Cannot retrieve saved sensor reading!"));

        return convertToResponse(refreshedReading);
    }
}
