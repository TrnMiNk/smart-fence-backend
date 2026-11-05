package smart_fence_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import smart_fence_backend.dto.SensorReadingRequest;
import smart_fence_backend.entity.DeviceComponent;
import smart_fence_backend.entity.SensorReading;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.SensorReadingRepository;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

class SensorReadingServiceTests {

    private SensorReadingRepository readingRepository;
    private DeviceComponentRepository componentRepository;
    private SensorReadingService service;
    private DeviceComponent rainSensor;
    private AtomicReference<SensorReading> persistedReading;

    @BeforeEach
    void setUp() {
        readingRepository = mock(SensorReadingRepository.class);
        componentRepository = mock(DeviceComponentRepository.class);
        service = new SensorReadingService(readingRepository, componentRepository);
        rainSensor = new DeviceComponent();
        ReflectionTestUtils.setField(rainSensor, "id", 10L);
        rainSensor.setComponentType("RAIN_SENSOR");
        when(componentRepository.findById(10L)).thenReturn(Optional.of(rainSensor));
        persistedReading = new AtomicReference<>();
        when(readingRepository.saveAndFlush(any(SensorReading.class))).thenAnswer(invocation -> {
            SensorReading saved = invocation.getArgument(0);
            persistedReading.set(saved);
            return saved;
        });
        when(readingRepository.findById(nullable(Long.class)))
                .thenAnswer(invocation -> Optional.ofNullable(persistedReading.get()));
    }

    @Test
    void acceptsAndPersistsRawAdcBoundariesAndDigitalRainState() {
        for (int level : new int[] {0, 720, 4095}) {
            SensorReadingRequest request = request(level != 0, level);

            var response = service.saveReading(10L, request);

            assertEquals(level, response.getRainLevel());
            assertEquals(level != 0, response.getRainDetected());
            assertSame(rainSensor, persistedReading.get().getComponent());
        }
        verify(readingRepository, times(3)).saveAndFlush(any(SensorReading.class));
    }

    @Test
    void rejectsMissingDigitalOrAnalogFieldsAndOutOfRangeAdc() {
        assertThrows(IllegalArgumentException.class, () -> service.saveReading(10L, request(null, 720)));
        assertThrows(IllegalArgumentException.class, () -> service.saveReading(10L, request(true, null)));
        assertThrows(IllegalArgumentException.class, () -> service.saveReading(10L, request(true, -1)));
        assertThrows(IllegalArgumentException.class, () -> service.saveReading(10L, request(false, 4096)));
        assertThrows(IllegalArgumentException.class, () -> service.saveReading(10L, null));

        verify(readingRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsComponentThatIsNotRainSensor() {
        DeviceComponent laser = new DeviceComponent();
        laser.setComponentType("LASER");
        when(componentRepository.findById(11L)).thenReturn(Optional.of(laser));

        assertThrows(IllegalArgumentException.class,
                () -> service.saveReading(11L, request(true, 720)));

        verify(readingRepository, never()).saveAndFlush(any());
    }

    private static SensorReadingRequest request(Boolean detected, Integer level) {
        SensorReadingRequest request = new SensorReadingRequest();
        request.setRainDetected(detected);
        request.setRainLevel(level);
        return request;
    }
}
