package com.hmdp.config;

import com.hmdp.mq.TopicConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "hmdp.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public NewTopic seckillOrderTopic() {
        return new NewTopic(TopicConstants.SECKILL_ORDER, 6, (short) 1);
    }

    @Bean
    public NewTopic seckillOrderDltTopic() {
        return new NewTopic(TopicConstants.SECKILL_ORDER + TopicConstants.DLT_SUFFIX, 6, (short) 1);
    }

    @Bean
    public NewTopic orderCompensationTopic() {
        return new NewTopic(TopicConstants.ORDER_COMPENSATION, 6, (short) 1);
    }

    @Bean
    public NewTopic orderCompensationDltTopic() {
        return new NewTopic(TopicConstants.ORDER_COMPENSATION + TopicConstants.DLT_SUFFIX, 6, (short) 1);
    }

    @Bean
    public NewTopic cacheInvalidationTopic() {
        return new NewTopic(TopicConstants.CACHE_INVALIDATION, 3, (short) 1);
    }

    @Bean
    public NewTopic cacheInvalidationDltTopic() {
        return new NewTopic(TopicConstants.CACHE_INVALIDATION + TopicConstants.DLT_SUFFIX, 3, (short) 1);
    }

    @Bean
    public SeekToCurrentErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            HmdpKafkaProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + TopicConstants.DLT_SUFFIX, record.partition()));
        return new SeekToCurrentErrorHandler(
                recoverer,
                new FixedBackOff(properties.getRetryInterval().toMillis(), properties.getRetryAttempts()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            SeekToCurrentErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        factory.setErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(3);
        return factory;
    }
}
