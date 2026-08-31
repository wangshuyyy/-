package com.hmdp.mq;

/**
 * 项目内 Kafka Topic 的统一定义，避免生产者和消费者使用不同字符串。
 */
public final class TopicConstants {

    public static final String SECKILL_ORDER = "seckill-voucher-order";
    public static final String ORDER_COMPENSATION = "voucher-order-compensation";
    public static final String CACHE_INVALIDATION = "cache-invalidation";
    public static final String DLT_SUFFIX = ".DLT";

    private TopicConstants() {
    }
}
