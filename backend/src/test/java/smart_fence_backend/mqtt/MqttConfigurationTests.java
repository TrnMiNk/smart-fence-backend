package smart_fence_backend.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import smart_fence_backend.repository.DeviceComponentRepository;
import smart_fence_backend.repository.DeviceRepository;
import smart_fence_backend.service.DeviceCommandService;
import smart_fence_backend.service.IntrusionEventService;
import smart_fence_backend.service.SensorReadingService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MqttConfigurationTests {

    private final ApplicationContextRunner mqttConfigContext = new ApplicationContextRunner()
            .withUserConfiguration(MqttConfig.class);

    @Test
    void disabledMqttDoesNotRequireHostOrCreateClientAndServiceStillLoads() {
        new ApplicationContextRunner()
                .withUserConfiguration(MqttConfig.class, MqttService.class)
                .withPropertyValues("mqtt.enabled=false").run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(MqttClient.class).isEmpty());
            assertTrue(context.getBeansOfType(MqttConnectOptions.class).isEmpty());
            assertNotNull(context.getBean(MqttService.class));
        });
    }

    @Test
    void enabledMqttCreatesClientAndConnectionOptions() {
        mqttConfigContext.withPropertyValues("mqtt.enabled=true", "mqtt.host=127.0.0.1")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(MqttClient.class).size());
                    assertEquals(1, context.getBeansOfType(MqttConnectOptions.class).size());
                    assertEquals("tcp://127.0.0.1:1883",
                            context.getBean(MqttClient.class).getServerURI());
                });
    }

    @Test
    void disabledMqttDoesNotCreateTopicSubscriptionRunner() {
        new ApplicationContextRunner()
                .withUserConfiguration(MqttSubscriber.class)
                .withBean(MqttService.class, () -> mock(MqttService.class))
                .withBean(DeviceCommandService.class, () -> mock(DeviceCommandService.class))
                .withBean(SensorReadingService.class, () -> mock(SensorReadingService.class))
                .withBean(IntrusionEventService.class, () -> mock(IntrusionEventService.class))
                .withBean(DeviceRepository.class, () -> mock(DeviceRepository.class))
                .withBean(DeviceComponentRepository.class, () -> mock(DeviceComponentRepository.class))
                .withBean(tools.jackson.databind.json.JsonMapper.class,
                        () -> mock(tools.jackson.databind.json.JsonMapper.class))
                .withBean(PendingSystemAckRegistry.class, () -> mock(PendingSystemAckRegistry.class))
                .withPropertyValues("mqtt.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(ApplicationRunner.class).isEmpty());
                });
    }

    @Test
    void enabledMqttCreatesTopicSubscriptionRunner() {
        new ApplicationContextRunner()
                .withUserConfiguration(MqttSubscriber.class)
                .withBean(MqttService.class, () -> mock(MqttService.class))
                .withBean(DeviceCommandService.class, () -> mock(DeviceCommandService.class))
                .withBean(SensorReadingService.class, () -> mock(SensorReadingService.class))
                .withBean(IntrusionEventService.class, () -> mock(IntrusionEventService.class))
                .withBean(DeviceRepository.class, () -> mock(DeviceRepository.class))
                .withBean(DeviceComponentRepository.class, () -> mock(DeviceComponentRepository.class))
                .withBean(tools.jackson.databind.json.JsonMapper.class,
                        () -> mock(tools.jackson.databind.json.JsonMapper.class))
                .withBean(PendingSystemAckRegistry.class, () -> mock(PendingSystemAckRegistry.class))
                .withPropertyValues("mqtt.enabled=true")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(ApplicationRunner.class).size());
                });
    }
}
