package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.limiter.annotation.RateLimiter;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    @RateLimiter(key = "seckill:", window = 1, limit = 5,
            message = "抢票请求过于频繁，请稍后再试", type = RateLimiter.LimitType.USER)
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @GetMapping("/{id}")
    public Result queryOrder(@PathVariable("id") Long orderId) {
        return voucherOrderService.queryOrder(orderId);
    }

    /** 学习项目使用模拟支付回调，不接入真实资金渠道。 */
    @PostMapping("/{id}/mock-pay")
    public Result mockPay(@PathVariable("id") Long orderId) {
        return voucherOrderService.mockPay(orderId);
    }

    @PostMapping("/{id}/cancel")
    public Result cancel(@PathVariable("id") Long orderId) {
        return voucherOrderService.cancelOrder(orderId);
    }
}
