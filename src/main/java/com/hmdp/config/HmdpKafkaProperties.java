package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.kafka")
public class HmdpKafkaProperties {

    private boolean enabled;
    private Duration sendTimeout = Duration.ofSeconds(3);
    private Duration retryInterval = Duration.ofSeconds(1);
    private Long retryAttempts = 3L;
}
