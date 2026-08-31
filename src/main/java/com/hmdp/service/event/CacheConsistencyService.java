package com.hmdp.service.event;

import com.hmdp.mq.CacheInvalidationEvent;
import com.hmdp.mq.KafkaDomainEventPublisher;
import com.hmdp.mq.TopicConstants;
import com.hmdp.service.cache.VoucherListCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Service
public class CacheConsistencyService {

    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private VoucherListCacheService voucherListCacheService;
    @Resource private KafkaDomainEventPublisher eventPublisher;

    /** 数据库事务提交后再删除缓存，避免读请求把未提交的旧值重新回填。 */
    public void invalidateAfterCommit(CacheInvalidationEvent event) {
        Runnable action = () -> invalidateAndBroadcast(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Kafka消费者调用；失败时抛异常，由Kafka进行固定间隔重试并最终转入DLT。 */
    public void invalidateFromMessage(CacheInvalidationEvent event) {
        invalidateLocal(event);
    }

    private void invalidateAndBroadcast(CacheInvalidationEvent event) {
        RuntimeException localFailure = null;
        try {
            invalidateLocal(event);
        } catch (RuntimeException e) {
            localFailure = e;
            log.warn("本地缓存删除失败，改由Kafka消息重试，event={}", event, e);
        }

        if (eventPublisher.isEnabled()) {
            try {
                // 广播同时用于删除失败重试和多实例Caffeine失效。
                eventPublisher.publish(TopicConstants.CACHE_INVALIDATION,
                        event.getCacheType() + ':' + event.getBusinessId(), event);
                return;
            } catch (RuntimeException e) {
                log.error("缓存失效消息发送失败，等待TTL兜底，event={}", event, e);
            }
        }
        if (localFailure != null) {
            log.error("缓存删除失败且Kafka不可用，等待TTL兜底，event={}", event);
        }
    }

    private void invalidateLocal(CacheInvalidationEvent event) {
        if (CacheInvalidationEvent.SHOP.equals(event.getCacheType())) {
            stringRedisTemplate.delete(CACHE_SHOP_KEY + event.getBusinessId());
            return;
        }
        if (CacheInvalidationEvent.VOUCHER_LIST.equals(event.getCacheType())) {
            voucherListCacheService.invalidate(event.getBusinessId());
            return;
        }
        throw new IllegalArgumentException("未知缓存类型: " + event.getCacheType());
    }
}
