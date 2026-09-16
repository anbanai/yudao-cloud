-- 订单积分发放时机配置及待生效积分支持
-- 兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL 版本。
-- 每个字段单独检查，脚本可重复执行；已存在的字段不会被修改。

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'member_config'
          AND column_name = 'point_trade_give_timing'
    ),
    'SELECT 1',
    'ALTER TABLE `member_config` ADD COLUMN `point_trade_give_timing` tinyint NOT NULL DEFAULT 1 COMMENT ''订单赠送积分发放时机：1 支付后，2 收货后'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'member_point_record'
          AND column_name = 'status'
    ),
    'SELECT 1',
    'ALTER TABLE `member_point_record` ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT ''状态：1 已生效，2 待生效，3 已作废'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'member_point_record'
          AND column_name = 'effective_time'
    ),
    'SELECT 1',
    'ALTER TABLE `member_point_record` ADD COLUMN `effective_time` datetime NULL COMMENT ''生效时间'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史流水默认视为已生效，使用创建时间补齐生效时间。
UPDATE `member_point_record`
SET `effective_time` = `create_time`
WHERE `status` = 1
  AND `effective_time` IS NULL;

SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'trade_order'
          AND column_name = 'point_give_timing'
    ),
    'SELECT 1',
    'ALTER TABLE `trade_order` ADD COLUMN `point_give_timing` tinyint NOT NULL DEFAULT 1 COMMENT ''赠送积分发放时机：1 支付后，2 收货后'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
