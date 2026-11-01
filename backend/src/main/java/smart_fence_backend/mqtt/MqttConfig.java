
package smart_fence_backend.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {

    private static final String BROKER_URL = "tcp://localhost:1883";

    private static final String CLIENT_ID = "smart-fence-backend";

    @Bean
    public MqttClient mqttClient() throws Exception {

        MqttClient client = new MqttClient(
                BROKER_URL,
                CLIENT_ID,
                new MemoryPersistence()
        );

        MqttConnectOptions options = new MqttConnectOptions();

        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        client.connect(options);

        System.out.println("MQTT connected to Mosquitto successfully!");

        return client;
    }
}
