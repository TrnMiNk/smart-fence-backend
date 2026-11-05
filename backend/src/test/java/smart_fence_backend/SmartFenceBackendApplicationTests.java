package smart_fence_backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SmartFenceBackendApplicationTests {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("smart_fence_context_test")
            .withUsername("context_test")
            .withPassword("context_test");

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void configureDedicatedTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Test
    void contextLoads() {
        Assertions.assertFalse(applicationContext.containsBean("mqttClient"));
        Assertions.assertFalse(applicationContext.containsBean("testMqttClient"));
        Assertions.assertFalse(applicationContext.containsBean("subscribeToTopics"));
    }
}
