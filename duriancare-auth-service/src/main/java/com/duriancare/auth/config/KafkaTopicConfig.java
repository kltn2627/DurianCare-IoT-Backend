package com.duriancare.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic authEventsTopic(AuthProperties properties) {
        return TopicBuilder.name(properties.eventsTopic())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
