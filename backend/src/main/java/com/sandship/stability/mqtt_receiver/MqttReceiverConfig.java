package com.sandship.stability.mqtt_receiver;

import com.sandship.stability.mqtt_receiver.MqttReceiverService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
public class MqttReceiverConfig {

    @Value("${spring.mqtt.url}")
    private String mqttUrl;

    @Value("${spring.mqtt.client-id}")
    private String clientId;

    @Value("${spring.mqtt.username}")
    private String username;

    @Value("${spring.mqtt.password}")
    private String password;

    @Value("${spring.mqtt.topics}")
    private String[] topics;

    @Value("${spring.mqtt.completion-timeout}")
    private int completionTimeout;

    @Value("${spring.mqtt.connection-timeout}")
    private int connectionTimeout;

    @Value("${spring.mqtt.keep-alive-interval}")
    private int keepAliveInterval;

    @Autowired
    private MqttReceiverService mqttReceiverService;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttUrl});
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean(name = "mqttReceiverInputChannel")
    public MessageChannel mqttReceiverInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttReceiverInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-inbound",
                        mqttClientFactory(), topics);
        adapter.setCompletionTimeout(completionTimeout);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttReceiverInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttReceiverInputChannel")
    public MessageHandler mqttReceiverInboundHandler() {
        return mqttReceiverService;
    }
}
