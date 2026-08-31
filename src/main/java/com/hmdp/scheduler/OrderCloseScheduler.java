package com.hmdp.scheduler;

import com.hmdp.service.IVoucherOrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class OrderCloseScheduler {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Scheduled(fixedDelayString = "${hmdp.seckill.close-scan-delay:60000}")
    public void closeExpiredOrders() {
        voucherOrderService.closeExpiredOrders();
    }
}
