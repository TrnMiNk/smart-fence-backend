package smart_fence_backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import smart_fence_backend.repository.IntrusionEventRepository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IntrusionEventReceiptIntegrationTests {

    private static final String DEVICE_CODE = "RECEIPT-TEST-DEVICE";
    private static final String RECEIPT_TOPIC = "smart-fence/" + DEVICE_CODE + "/event-receipts";
    private static final String EVENT_ID = "b54aaf8e-480a-4e0e-ae41-f425c8ce512a";
    private static final String OCCURRED_AT = "2026-11-03T04:05:06.123Z";
    private static final String DESCRIPTION = "Laser beam interrupted";

    @Container
    static final MySQLContainer MY_SQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("receipt_test")
            .withUsername("receipt_test")
            .withPassword("receipt_test")
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
    private IntrusionEventRepository eventRepository;

    @MockitoBean
    private MqttService mqttService;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM intrusion_events");
        jdbc.update("DELETE FROM device_components");
        jdbc.update("DELETE FROM devices");
        jdbc.update("INSERT INTO devices (device_code, device_name, status, system_state) VALUES (?, ?, 'ONLINE', 'ON')",
                DEVICE_CODE, "Receipt integration test device");
        Long deviceId = jdbc.queryForObject("SELECT id FROM devices WHERE device_code = ?", Long.class, DEVICE_CODE);
        jdbc.update("INSERT INTO device_components (device_id, component_code, component_name, component_type, status) VALUES (?, 'LASER-1', 'Laser', 'LASER', 'ACTIVE')",
                deviceId);

        clearInvocations(mqttService);
    }

    @Test
    void validEventCommitsBeforePublishingSavedReceiptWithExpectedTopicAndPayload() throws Exception {
        doAnswer(invocation -> {
            assertEquals(RECEIPT_TOPIC, invocation.getArgument(0));
            JsonNode payload = new ObjectMapper().readTree((String) invocation.getArgument(1));
            assertEquals(EVENT_ID, payload.path("eventId").asText());
            assertEquals("SAVED", payload.path("status").asText());
            assertFalse(payload.has("reason"));
            assertTrue(payload.path("acknowledgedAt").asText().length() > 0);
            assertEquals(1, countEventFromIndependentConnection());
            return null;
        }).when(mqttService).publish(anyString(), anyString());

        sendEvent(EVENT_ID, DESCRIPTION, OCCURRED_AT);

        verify(mqttService).publish(eq(RECEIPT_TOPIC), anyString());
        assertEquals(1, eventRepository.count());
    }

    @Test
    void sameEventIdAndContentPublishesDuplicateReceipt() throws Exception {
        insertExistingEvent(EVENT_ID, DESCRIPTION, "2026-11-03 04:05:06.123");
        assertReceiptAfterSend("DUPLICATE", null);
        assertEquals(1, eventRepository.count());
    }

    @Test
    void sameEventIdWithDifferentContentPublishesConflictReceipt() throws Exception {
        insertExistingEvent(EVENT_ID, DESCRIPTION, "2026-11-03 04:05:06.123");
        assertReceiptAfterSend("REJECTED", "EVENT_ID_CONFLICT", "Changed description");
        assertEquals(1, eventRepository.count());
    }

    @Test
    void persistenceFailureRollsBackAndDoesNotPublishSavedReceipt() throws Exception {
        jdbc.execute("ALTER TABLE intrusion_events ADD CONSTRAINT chk_receipt_test_failure "
                + "CHECK (description <> 'force-persistence-failure')");

        sendEvent(EVENT_ID, "force-persistence-failure", OCCURRED_AT);

        verify(mqttService, never()).publish(anyString(), anyString());
        assertEquals(0, countEventFromIndependentConnection());
        assertEquals(0, eventRepository.count());
    }

    private void assertReceiptAfterSend(String expectedStatus, String expectedReason) throws Exception {
        assertReceiptAfterSend(expectedStatus, expectedReason, DESCRIPTION);
    }

    private void assertReceiptAfterSend(String expectedStatus, String expectedReason, String description) throws Exception {
        doAnswer(invocation -> {
            assertEquals(RECEIPT_TOPIC, invocation.getArgument(0));
            JsonNode payload = new ObjectMapper().readTree((String) invocation.getArgument(1));
            assertEquals(EVENT_ID, payload.path("eventId").asText());
            assertEquals(expectedStatus, payload.path("status").asText());
            if (expectedReason == null) assertFalse(payload.has("reason"));
            else assertEquals(expectedReason, payload.path("reason").asText());
            assertTrue(payload.path("acknowledgedAt").asText().length() > 0);
            assertEquals(1, countEventFromIndependentConnection());
            return null;
        }).when(mqttService).publish(anyString(), anyString());

        sendEvent(EVENT_ID, description, OCCURRED_AT);

        verify(mqttService).publish(eq(RECEIPT_TOPIC), anyString());
    }

    private void sendEvent(String eventId, String description, String occurredAt) {
        String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"LASER_BLOCKED\","
                + "\"description\":\"" + description + "\",\"occurredAt\":\"" + occurredAt + "\"}";
        ReflectionTestUtils.invokeMethod(subscriber, "handleIntrusionEvent",
                "smart-fence/" + DEVICE_CODE + "/events", payload);
    }

    private void insertExistingEvent(String eventId, String description, String occurredAt) {
        Long deviceId = jdbc.queryForObject("SELECT id FROM devices WHERE device_code = ?", Long.class, DEVICE_CODE);
        Long componentId = jdbc.queryForObject("SELECT id FROM device_components WHERE device_id = ?", Long.class, deviceId);
        jdbc.update("INSERT INTO intrusion_events (device_id, component_id, event_id, occurred_at, event_type, event_status, description) "
                + "VALUES (?, ?, ?, ?, 'LASER_BLOCKED', 'NEW', ?)", deviceId, componentId, eventId, occurredAt, description);
    }

    private int countEventFromIndependentConnection() throws Exception {
        try (Connection connection = DriverManager.getConnection(MY_SQL.getJdbcUrl(), MY_SQL.getUsername(), MY_SQL.getPassword());
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM intrusion_events WHERE event_id = '" + EVENT_ID + "'")) {
            assertTrue(connection.getAutoCommit());
            result.next();
            return result.getInt(1);
        }
    }
}
