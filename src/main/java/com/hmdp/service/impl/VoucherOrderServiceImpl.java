package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.SeckillProperties;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.KafkaDomainEventPublisher;
import com.hmdp.mq.OrderCompensationEvent;
import com.hmdp.mq.SeckillOrderEvent;
import com.hmdp.mq.TopicConstants;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final int UNPAID = 1;
    private static final int CANCELLED = 4;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource private RedisIdWorker redisIdWorker;
    @Resource private RedissonClient redissonClient;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private VoucherOrderTransactionalService transactionalService;
    @Resource private KafkaDomainEventPublisher eventPublisher;
    @Resource private SeckillProperties seckillProperties;

    @Override
    public Result seckillVoucher(Long voucherId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        return "async".equalsIgnoreCase(seckillProperties.getMode())
                ? asyncSeckill(voucherId) : syncSeckill(voucherId);
    }

    private Result syncSeckill(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        if (!lock.tryLock()) {
            return Result.fail("请求处理中，请勿重复提交");
        }
        try {
            transactionalService.createSyncOrder(orderId, userId, voucherId);
            return Result.ok(orderId);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private Result asyncSeckill(Long voucherId) {
        if (!eventPublisher.isEnabled()) {
            return Result.fail("异步秒杀需要启用 Kafka");
        }
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(),
                String.valueOf(orderId), String.valueOf(System.currentTimeMillis()));
        if (result == null) {
            return Result.fail("秒杀服务暂不可用");
        }
        int code = result.intValue();
        if (code != 0) {
            return Result.fail(seckillError(code));
        }

        SeckillOrderEvent event = new SeckillOrderEvent(orderId, userId, voucherId);
        try {
            // 只等待 Broker 接收；MySQL 扣库存和订单创建仍由消费者异步执行。
            eventPublisher.publish(TopicConstants.SECKILL_ORDER, String.valueOf(orderId), event);
        } catch (RuntimeException e) {
            transactionalService.rollbackReservation(event);
            log.error("秒杀消息发送失败，已回滚Redis资格，orderId={}", orderId, e);
            return Result.fail("订单排队失败，请稍后重试");
        }
        return Result.ok(orderId);
    }

    @Override
    public void createVoucherOrder(SeckillOrderEvent event) {
        transactionalService.createAsyncOrder(event);
    }

    @Override
    public void rollbackReservation(SeckillOrderEvent event) {
        transactionalService.rollbackReservation(event);
    }

    @Override
    public void compensateCancelledOrder(OrderCompensationEvent event) {
        transactionalService.compensateCancelledOrder(event);
    }

    @Override
    public Result queryOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (!canAccess(order)) {
            return Result.fail("订单不存在");
        }
        if (isExpired(order)) {
            closeAndDispatch(order, "passive-close");
            order = getById(orderId);
        }
        return Result.ok(order);
    }

    @Override
    public Result mockPay(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (!canAccess(order)) {
            return Result.fail("订单不存在");
        }
        if (isExpired(order)) {
            closeAndDispatch(order, "pay-after-timeout");
            return Result.fail("订单已超时关闭");
        }
        boolean paid = transactionalService.payIfUnpaid(orderId, UserHolder.getUser().getId());
        if (paid) {
            return Result.ok();
        }
        VoucherOrder latest = getById(orderId);
        return Result.fail(latest != null && latest.getStatus() != null && latest.getStatus() == CANCELLED
                ? "订单已取消，无法支付" : "订单状态已变化，请勿重复支付");
    }

    @Override
    public Result cancelOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (!canAccess(order)) {
            return Result.fail("订单不存在");
        }
        return closeAndDispatch(order, "user-cancel")
                ? Result.ok() : Result.fail("订单状态已变化，无法取消");
    }

    @Override
    public void closeExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minus(seckillProperties.getOrderTimeout());
        List<VoucherOrder> expired = list(new QueryWrapper<VoucherOrder>()
                .eq("status", UNPAID).lt("create_time", deadline).orderByAsc("create_time")
                .last("LIMIT " + seckillProperties.getCloseScanBatchSize()));
        for (VoucherOrder order : expired) {
            closeAndDispatch(order, "scheduled-close");
        }

        // Kafka短暂不可用时，已取消且尚未回补完成的订单会被重新投递。
        List<VoucherOrder> pending = list(new QueryWrapper<VoucherOrder>()
                .eq("status", CANCELLED).lt("stock_return_state", 2).orderByAsc("update_time")
                .last("LIMIT " + seckillProperties.getCloseScanBatchSize()));
        for (VoucherOrder order : pending) {
            dispatchCompensation(order, "scheduled-retry");
        }
    }

    private boolean closeAndDispatch(VoucherOrder order, String reason) {
        if (order == null || !transactionalService.closeIfUnpaid(order.getId())) {
            return false;
        }
        dispatchCompensation(order, reason);
        return true;
    }

    private void dispatchCompensation(VoucherOrder order, String reason) {
        OrderCompensationEvent event = new OrderCompensationEvent(
                order.getId(), order.getUserId(), order.getVoucherId(), reason);
        if (!eventPublisher.isEnabled()) {
            transactionalService.compensateCancelledOrder(event);
            return;
        }
        try {
            eventPublisher.publish(TopicConstants.ORDER_COMPENSATION, String.valueOf(order.getId()), event);
        } catch (RuntimeException e) {
            // 保持 stock_return_state=0，定时任务下一轮会重新投递。
            log.error("补偿消息发送失败，等待定时任务重试，orderId={}", order.getId(), e);
        }
    }

    private boolean canAccess(VoucherOrder order) {
        return order != null && UserHolder.getUser() != null
                && order.getUserId().equals(UserHolder.getUser().getId());
    }

    private boolean isExpired(VoucherOrder order) {
        return order.getStatus() != null && order.getStatus() == UNPAID
                && order.getCreateTime() != null
                && order.getCreateTime().plus(seckillProperties.getOrderTimeout()).isBefore(LocalDateTime.now());
    }

    private String seckillError(int code) {
        switch (code) {
            case 1: return "库存不足";
            case 2: return "不能重复下单";
            case 3: return "秒杀信息尚未预热";
            case 4: return "秒杀尚未开始";
            case 5: return "秒杀已经结束";
            default: return "秒杀失败";
        }
    }
}
