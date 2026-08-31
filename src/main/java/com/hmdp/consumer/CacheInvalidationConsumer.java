package com.hmdp.consumer;

import cn.hutool.json.JSONUtil;
import com.hmdp.mq.CacheInvalidationEvent;
import com.hmdp.mq.TopicConstants;
import com.hmdp.service.event.CacheConsistencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "hmdp.kafka", name = "enabled", havingValue = "true")
public class CacheInvalidationConsumer {

    @Resource
    private CacheConsistencyService cacheConsistencyService;

    @KafkaListener(topics = TopicConstants.CACHE_INVALIDATION, groupId = "hmdp-cache-${random.uuid}")
    public void invalidate(String payload) {
        cacheConsistencyService.invalidateFromMessage(
                JSONUtil.toBean(payload, CacheInvalidationEvent.class));
    }

    @KafkaListener(topics = TopicConstants.CACHE_INVALIDATION + TopicConstants.DLT_SUFFIX,
            groupId = "hmdp-cache-dlt-group")
    public void logDlt(String payload) {
        log.error("缓存删除消息进入DLT，TTL将作为最终兜底，payload={}", payload);
    }
}
