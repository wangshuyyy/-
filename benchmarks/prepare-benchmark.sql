-- Run against database hmdp before each seckill benchmark.
-- Voucher id 100 is reserved for pressure testing.
INSERT INTO tb_voucher
    (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status)
VALUES
    (100, 1, '压测专用演出票', '仅用于本地模拟支付压测', '压测数据，不可用于生产', 100, 100, 1, 1)
ON DUPLICATE KEY UPDATE status = 1, update_time = CURRENT_TIMESTAMP;

INSERT INTO tb_seckill_voucher (voucher_id, stock, begin_time, end_time)
VALUES (100, 1000000, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY))
ON DUPLICATE KEY UPDATE
    stock = 1000000,
    begin_time = DATE_SUB(NOW(), INTERVAL 1 DAY),
    end_time = DATE_ADD(NOW(), INTERVAL 30 DAY);

DELETE FROM tb_voucher_order WHERE voucher_id = 100;
