package smart_fence_backend.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
public class MqttConfig {

    @Bean
    @Profile("!test")
    @ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
    public MqttConnectOptions mqttConnectOptions(
            @Value("${mqtt.username:}") String username,
            @Value("${mqtt.password:}") String password) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        return options;
    }

    @Bean
    @Profile("!test")
    @ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
    public MqttClient mqttClient(
            @Value("${mqtt.host:}") String host,
            @Value("${mqtt.port:1883}") int port,
            @Value("${mqtt.client-id:smart-fence-backend}") String clientId) throws Exception {
        return createClient(host, port, clientId);
    }

    @Bean
    @Profile("test")
    @ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
    public MqttConnectOptions testMqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);
        options.setConnectionTimeout(1);
        options.setKeepAliveInterval(10);
        return options;
    }

    @Bean
    @Profile("test")
    @ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true", matchIfMissing = true)
    public MqttClient testMqttClient(
            @Value("${mqtt.host:}") String host,
            @Value("${mqtt.port:1883}") int port,
            @Value("${mqtt.client-id:smart-fence-backend-test}") String clientId) throws Exception {
        return createClient(host, port, clientId);
    }

    private MqttClient createClient(String host, int port, String clientId) throws Exception {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("mqtt.host must be set when mqtt.enabled=true");
        }
        return new MqttClient("tcp://" + host + ":" + port, clientId,
                new MemoryPersistence());
    }
}
