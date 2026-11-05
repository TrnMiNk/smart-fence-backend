package smart_fence_backend.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import smart_fence_backend.entity.SensorReading;
import smart_fence_backend.repository.SensorReadingRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class Yl83TelemetryIntegrationTests {

    private static final String DEVICE_CODE = "YL83-TEST-DEVICE";

    @Container
    static final MySQLContainer MY_SQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("yl83_test")
            .withUsername("yl83_test")
            .withPassword("yl83_test")
            .withInitScript("application-receipt-test-schema.sql");

    @DynamicPropertySource
    static void configureContainerDatabase(DynamicPropertyRegistry registry) {
        MY_SQL.start();
        registry.add("spring.datasource.url", MY_SQL::getJdbcUrl);
        registry.add("spring.datasource.username", MY_SQL::getUsername);
        registry.add("spring.datasource.password", MY_SQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MqttSubscriber subscriber;

    @Autowired
    private SensorReadingRepository readingRepository;

    @MockitoBean
    private MqttService mqttService;

    private Long rainComponentId;

    @BeforeEach
    void prepareDeviceAndComponents() {
        jdbc.update("DELETE FROM sensor_readings");
        jdbc.update("DELETE FROM intrusion_events");
        jdbc.update("DELETE FROM device_components");
        jdbc.update("DELETE FROM devices");
        jdbc.update("INSERT INTO devices (device_code, device_name, status, system_state) VALUES (?, 'YL83 Test', 'ONLINE', 'ON')",
                DEVICE_CODE);
        Long deviceId = jdbc.queryForObject("SELECT id FROM devices WHERE device_code = ?", Long.class, DEVICE_CODE);
        jdbc.update("INSERT INTO device_components (device_id, component_code, component_name, component_type) VALUES (?, 'LASER-1', 'Laser', 'LASER')",
                deviceId);
        jdbc.update("INSERT INTO device_components (device_id, component_code, component_name, component_type) VALUES (?, 'RAIN-1', 'YL-83', 'RAIN_SENSOR')",
                deviceId);
        rainComponentId = jdbc.queryForObject(
                "SELECT id FROM device_components WHERE device_id = ? AND component_type = 'RAIN_SENSOR'",
                Long.class, deviceId);
    }

    @Test
    void telemetryStoresDoBooleanAndRawAoOnTheDeviceRainComponent() {
        sendTelemetry("{\"rainDetected\":true,\"rainLevel\":720}");

        List<SensorReading> readings = readingRepository.findAll();
        assertEquals(1, readings.size());
        SensorReading reading = readings.getFirst();
        assertEquals(rainComponentId, reading.getComponent().getId());
        assertTrue(reading.getRainDetected());
        assertEquals(720, reading.getRainLevel());
    }

    @Test
    void adcBoundaryValuesAreStoredWithoutScaling() {
        sendTelemetry("{\"rainDetected\":false,\"rainLevel\":0}");
        sendTelemetry("{\"rainDetected\":true,\"rainLevel\":4095}");

        assertEquals(List.of(0, 4095), readingRepository.findAll().stream()
                .map(SensorReading::getRainLevel).sorted().toList());
        assertEquals(List.of(false, true), readingRepository.findAll().stream()
                .map(SensorReading::getRainDetected).sorted().toList());
    }

    @Test
    void malformedMissingAndWronglyTypedTelemetryAreIgnored() {
        List<String> invalidPayloads = List.of(
                "not-json",
                "[]",
                "{\"rainLevel\":720}",
                "{\"rainDetected\":true}",
                "{\"rainDetected\":\"true\",\"rainLevel\":720}",
                "{\"rainDetected\":true,\"rainLevel\":\"720\"}",
                "{\"rainDetected\":true,\"rainLevel\":720.5}",
                "{\"rainDetected\":true,\"rainLevel\":-1}",
                "{\"rainDetected\":false,\"rainLevel\":4096}"
        );

        invalidPayloads.forEach(this::sendTelemetry);

        assertTrue(readingRepository.findAll().isEmpty());
    }

    private void sendTelemetry(String payload) {
        ReflectionTestUtils.invokeMethod(subscriber, "handleTelemetry",
                "smart-fence/" + DEVICE_CODE + "/telemetry", payload);
    }
}
