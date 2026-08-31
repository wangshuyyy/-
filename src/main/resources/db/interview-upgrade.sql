-- 已导入旧版 hmdp.sql 时执行本增量脚本；全新环境直接使用更新后的 hmdp.sql。
ALTER TABLE `tb_voucher_order`
    ADD COLUMN `stock_return_state` tinyint(1) UNSIGNED NOT NULL DEFAULT 0
        COMMENT '库存回补状态：0未回补；1处理中；2已完成' AFTER `status`,
    ADD UNIQUE INDEX `uk_user_voucher` (`user_id`, `voucher_id`),
    ADD INDEX `idx_status_create_time` (`status`, `create_time`, `id`);

ALTER TABLE `tb_voucher`
    ADD INDEX `idx_shop_id` (`shop_id`);
