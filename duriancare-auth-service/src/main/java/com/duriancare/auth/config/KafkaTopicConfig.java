package com.duriancare.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.common.config.TopicConfig;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic authEventsTopic(AuthProperties properties) {
        return TopicBuilder.name(properties.eventsTopic())
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "600000")
                .config(TopicConfig.SEGMENT_MS_CONFIG, "60000")
                .build();
    }
}
