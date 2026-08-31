package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.cache.VoucherListCacheService;
import com.hmdp.service.event.CacheConsistencyService;
import com.hmdp.mq.CacheInvalidationEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private VoucherListCacheService voucherListCacheService;
    @Resource
    private CacheConsistencyService cacheConsistencyService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 【秒杀优惠券信息多级缓存改造】查询优惠券信息（L1 Caffeine + L2 Redis）
        List<Voucher> vouchers = voucherListCacheService.getVoucherByShopId(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        afterCommit(() -> warmupSeckillVoucher(seckillVoucher));
        cacheConsistencyService.invalidateAfterCommit(
                new CacheInvalidationEvent(CacheInvalidationEvent.VOUCHER_LIST, voucher.getShopId()));
    }

    @Override
    @Transactional
    public Result updateVoucher(Voucher voucher) {
        if (voucher.getId() == null) {
            return Result.fail("票务id不能为空");
        }
        Voucher old = getById(voucher.getId());
        if (old == null) {
            return Result.fail("票务不存在");
        }
        updateById(voucher);
        SeckillVoucher seckill = seckillVoucherService.getById(voucher.getId());
        if (seckill != null) {
            if (voucher.getStock() != null) seckill.setStock(voucher.getStock());
            if (voucher.getBeginTime() != null) seckill.setBeginTime(voucher.getBeginTime());
            if (voucher.getEndTime() != null) seckill.setEndTime(voucher.getEndTime());
            seckillVoucherService.updateById(seckill);
        }
        Long shopId = voucher.getShopId() == null ? old.getShopId() : voucher.getShopId();
        afterCommit(() -> {
            if (seckill != null) warmupSeckillVoucher(seckill);
        });
        cacheConsistencyService.invalidateAfterCommit(
                new CacheInvalidationEvent(CacheInvalidationEvent.VOUCHER_LIST, shopId));
        return Result.ok();
    }

    public void warmupSeckillVoucher(SeckillVoucher voucher) {
        if (voucher == null || voucher.getVoucherId() == null || voucher.getStock() == null
                || voucher.getBeginTime() == null || voucher.getEndTime() == null) {
            return;
        }
        Long id = voucher.getVoucherId();
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + id, voucher.getStock().toString());
        stringRedisTemplate.opsForValue().set("seckill:begin:" + id,
                String.valueOf(voucher.getBeginTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()));
        stringRedisTemplate.opsForValue().set("seckill:end:" + id,
                String.valueOf(voucher.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()));
    }

    private void afterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }
}
