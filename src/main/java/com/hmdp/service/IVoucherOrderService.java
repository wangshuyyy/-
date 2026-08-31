package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.mq.OrderCompensationEvent;
import com.hmdp.mq.SeckillOrderEvent;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(SeckillOrderEvent event);

    void rollbackReservation(SeckillOrderEvent event);

    void compensateCancelledOrder(OrderCompensationEvent event);

    Result queryOrder(Long orderId);

    Result mockPay(Long orderId);

    Result cancelOrder(Long orderId);

    void closeExpiredOrders();
}
