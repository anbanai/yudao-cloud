-- 交易配置增加企微客服二维码图片地址。可重复执行，适用于已部署旧版本数据库。
-- 发布更新后的交易服务前，请先在目标 MySQL 数据库执行本脚本。
SET @trade_kefu_wechat_qr_code_url_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'trade_config'
    AND COLUMN_NAME = 'kefu_wechat_qr_code_url'
);
SET @trade_kefu_wechat_qr_code_url_sql := IF(
  @trade_kefu_wechat_qr_code_url_exists = 0,
  'ALTER TABLE `trade_config` ADD COLUMN `kefu_wechat_qr_code_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT ''企微客服二维码图片地址'' AFTER `brokerage_withdraw_types`',
  'SELECT 1'
);
PREPARE trade_kefu_wechat_qr_code_url_stmt FROM @trade_kefu_wechat_qr_code_url_sql;
EXECUTE trade_kefu_wechat_qr_code_url_stmt;
DEALLOCATE PREPARE trade_kefu_wechat_qr_code_url_stmt;
-- Existing MySQL deployments must run this repeatable upgrade before starting the updated trade service.
