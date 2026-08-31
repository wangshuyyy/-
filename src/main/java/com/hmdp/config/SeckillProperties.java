package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.seckill")
public class SeckillProperties {

    /** sync 用于基线压测，async 使用 Redis Lua + Kafka。 */
    private String mode = "sync";
    private Duration orderTimeout = Duration.ofMinutes(15);
    private Duration closeScanDelay = Duration.ofMinutes(1);
    private Integer closeScanBatchSize = 100;
}
