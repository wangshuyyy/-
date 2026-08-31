package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.OrderCompensationEvent;
import com.hmdp.mq.SeckillOrderEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

@Service
public class VoucherOrderTransactionalService {

    private static final int UNPAID = 1;
    private static final int PAID = 2;
    private static final int CANCELLED = 4;

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT;

    static {
        ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_rollback.lua"));
        ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional
    public void createAsyncOrder(SeckillOrderEvent event) {
        if (voucherOrderMapper.selectById(event.getOrderId()) != null) {
            return;
        }

        VoucherOrder order = new VoucherOrder()
                .setId(event.getOrderId())
                .setUserId(event.getUserId())
                .setVoucherId(event.getVoucherId())
                .setStatus(UNPAID)
                .setStockReturnState(0);

        // 先插入订单。重复消息或一人一票唯一索引冲突会在扣库存之前失败。
        voucherOrderMapper.insert(order);
        int updated = decrementDatabaseStock(event.getVoucherId());
        if (updated == 0) {
            throw new IllegalStateException("MySQL 库存不足，订单事务回滚: " + event.getOrderId());
        }
    }

    @Transactional
    public void createSyncOrder(Long orderId, Long userId, Long voucherId) {
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if (voucher == null) {
            throw new IllegalStateException("票务不存在");
        }
        if (voucher.getBeginTime().isAfter(now)) {
            throw new IllegalStateException("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(now)) {
            throw new IllegalStateException("秒杀已经结束");
        }
        Integer count = voucherOrderMapper.selectCount(new QueryWrapper<VoucherOrder>()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId));
        if (count != null && count > 0) {
            throw new IllegalStateException("不能重复下单");
        }

        VoucherOrder order = new VoucherOrder()
                .setId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId)
                .setStatus(UNPAID)
                .setStockReturnState(0);
        voucherOrderMapper.insert(order);
        if (decrementDatabaseStock(voucherId) == 0) {
            throw new IllegalStateException("库存不足");
        }
    }

    public boolean payIfUnpaid(Long orderId, Long userId) {
        return voucherOrderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                .set("status", PAID)
                .set("pay_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq(userId != null, "user_id", userId)
                .eq("status", UNPAID)) == 1;
    }

    public boolean closeIfUnpaid(Long orderId) {
        return voucherOrderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                .set("status", CANCELLED)
                .eq("id", orderId)
                .eq("status", UNPAID)) == 1;
    }

    /**
     * MySQL 与 Redis 无法使用同一个本地事务，因此以订单回补状态作为幂等进度。
     * Redis Lua 本身也会检查用户资格标记，重复执行不会重复增加库存。
     */
    @Transactional
    public void compensateCancelledOrder(OrderCompensationEvent event) {
        VoucherOrder order = voucherOrderMapper.selectById(event.getOrderId());
        if (order == null || order.getStatus() == null || order.getStatus() != CANCELLED) {
            return;
        }
        int state = order.getStockReturnState() == null ? 0 : order.getStockReturnState();
        if (state >= 2) {
            return;
        }

        if (state == 0) {
            int claimed = voucherOrderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                    .set("stock_return_state", 1)
                    .eq("id", event.getOrderId())
                    .eq("status", CANCELLED)
                    .eq("stock_return_state", 0));
            if (claimed == 0) {
                return;
            }
            seckillVoucherMapper.update(null, new UpdateWrapper<SeckillVoucher>()
                    .setSql("stock = stock + 1")
                    .eq("voucher_id", event.getVoucherId()));
        }

        rollbackRedis(event.getVoucherId(), event.getUserId());
        voucherOrderMapper.update(null, new UpdateWrapper<VoucherOrder>()
                .set("stock_return_state", 2)
                .eq("id", event.getOrderId())
                .eq("stock_return_state", 1));
    }

    public void rollbackReservation(SeckillOrderEvent event) {
        rollbackRedis(event.getVoucherId(), event.getUserId());
    }

    private int decrementDatabaseStock(Long voucherId) {
        return seckillVoucherMapper.update(null, new UpdateWrapper<SeckillVoucher>()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0));
    }

    private void rollbackRedis(Long voucherId, Long userId) {
        Long result = stringRedisTemplate.execute(
                ROLLBACK_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        if (result == null) {
            throw new IllegalStateException("Redis 库存回补执行失败");
        }
    }
}
