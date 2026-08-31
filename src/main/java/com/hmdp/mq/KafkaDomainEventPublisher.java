package com.hmdp.mq;

import cn.hutool.json.JSONUtil;
import com.hmdp.config.HmdpKafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaDomainEventPublisher {

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;
    @Resource
    private HmdpKafkaProperties kafkaProperties;

    public boolean isEnabled() {
        return kafkaProperties.isEnabled();
    }

    /**
     * 等待 Broker 确认只表示 Kafka 已接收消息，订单写库仍由消费者异步完成。
     */
    public void publish(String topic, String key, Object event) {
        if (!isEnabled()) {
            throw new IllegalStateException("Kafka 未启用，请使用 docker profile 或切换同步秒杀模式");
        }
        try {
            kafkaTemplate.send(topic, key, JSONUtil.toJsonStr(event))
                    .get(kafkaProperties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka 消息发送失败: " + topic, e);
        }
    }
}
