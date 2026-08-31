-- 微信物流查询组件 token。脚本可重复执行。
SET @wechat_waybill_token_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_order'
      AND COLUMN_NAME = 'wechat_waybill_token'
);

SET @wechat_waybill_token_ddl = IF(
    @wechat_waybill_token_column_exists = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `wechat_waybill_token` VARCHAR(512) NULL COMMENT ''微信物流查询组件运单令牌'' AFTER `logistics_no`',
    'SELECT 1'
);

PREPARE wechat_waybill_token_stmt FROM @wechat_waybill_token_ddl;
EXECUTE wechat_waybill_token_stmt;
DEALLOCATE PREPARE wechat_waybill_token_stmt;
