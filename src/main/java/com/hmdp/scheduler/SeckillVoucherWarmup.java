package com.hmdp.scheduler;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.impl.VoucherServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillVoucherWarmup {

    @Resource private ISeckillVoucherService seckillVoucherService;
    @Resource private VoucherServiceImpl voucherService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        int count = 0;
        for (SeckillVoucher voucher : seckillVoucherService.list()) {
            voucherService.warmupSeckillVoucher(voucher);
            count++;
        }
        log.info("秒杀票务预热完成，数量={}", count);
    }
}
