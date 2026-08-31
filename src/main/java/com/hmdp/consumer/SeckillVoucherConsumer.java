package com.hmdp.consumer;

import cn.hutool.json.JSONUtil;
import com.hmdp.mq.OrderCompensationEvent;
import com.hmdp.mq.SeckillOrderEvent;
import com.hmdp.mq.TopicConstants;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "hmdp.kafka", name = "enabled", havingValue = "true")
public class SeckillVoucherConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @KafkaListener(topics = TopicConstants.SECKILL_ORDER, groupId = "hmdp-order-group")
    public void createOrder(String payload) {
        SeckillOrderEvent event = JSONUtil.toBean(payload, SeckillOrderEvent.class);
        voucherOrderService.createVoucherOrder(event);
    }

    /** 订单写库重试耗尽后恢复Redis库存和一人一票资格。 */
    @KafkaListener(topics = TopicConstants.SECKILL_ORDER + TopicConstants.DLT_SUFFIX,
            groupId = "hmdp-order-dlt-group")
    public void compensateOrderDlt(String payload) {
        try {
            SeckillOrderEvent event = JSONUtil.toBean(payload, SeckillOrderEvent.class);
            voucherOrderService.rollbackReservation(event);
            log.error("订单消息进入DLT，已执行Redis资格回滚，orderId={}", event.getOrderId());
        } catch (Exception e) {
            // DLT监听器不继续抛出，避免形成 .DLT.DLT；保留日志供人工或对账任务处理。
            log.error("订单DLT补偿失败，payload={}", payload, e);
        }
    }

    @KafkaListener(topics = TopicConstants.ORDER_COMPENSATION, groupId = "hmdp-compensation-group")
    public void compensateCancelledOrder(String payload) {
        OrderCompensationEvent event = JSONUtil.toBean(payload, OrderCompensationEvent.class);
        voucherOrderService.compensateCancelledOrder(event);
    }

    @KafkaListener(topics = TopicConstants.ORDER_COMPENSATION + TopicConstants.DLT_SUFFIX,
            groupId = "hmdp-compensation-dlt-group")
    public void logCompensationDlt(String payload) {
        // stock_return_state 未完成，定时关单任务会持续扫描并重新投递。
        log.error("库存回补消息进入DLT，等待定时扫描重新投递，payload={}", payload);
    }
}
