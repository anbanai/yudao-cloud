-- 文件存储公开/私有双默认迁移（MySQL 5.7+）
-- 部署后需要在“基础设施 -> 文件配置”分别设置公开默认和私有默认。

SET @file_config_private_count = (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'infra_file_config'
    AND `column_name` = 'private'
);
SET @file_config_private_sql = IF(
  @file_config_private_count = 0,
  'ALTER TABLE `infra_file_config` ADD COLUMN `private` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否为私有存储'' AFTER `master`',
  'SELECT 1'
);
PREPARE file_config_private_stmt FROM @file_config_private_sql;
EXECUTE file_config_private_stmt;
DEALLOCATE PREPARE file_config_private_stmt;

SET @infra_file_config_id_index_count = (
  SELECT COUNT(*) FROM `information_schema`.`statistics`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'infra_file'
    AND `index_name` = 'idx_infra_file_config_id'
);
SET @infra_file_config_id_index_sql = IF(
  @infra_file_config_id_index_count = 0,
  'ALTER TABLE `infra_file` ADD KEY `idx_infra_file_config_id` (`config_id`)',
  'SELECT 1'
);
PREPARE infra_file_config_id_index_stmt FROM @infra_file_config_id_index_sql;
EXECUTE infra_file_config_id_index_stmt;
DEALLOCATE PREPARE infra_file_config_id_index_stmt;

-- S3 的“公开访问”是唯一事实来源；非 S3 客户端保持公开。
UPDATE `infra_file_config`
SET `private` = IF(
  `storage` = 20
  AND JSON_UNQUOTE(JSON_EXTRACT(IF(JSON_VALID(`config`), `config`, '{}'), '$.enablePublicAccess')) = 'false',
  b'1', b'0'
);

SET @file_config_master_group_count = (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'infra_file_config'
    AND `column_name` = 'master_group'
);
SET @file_config_master_group_sql = IF(
  @file_config_master_group_count = 0,
  'ALTER TABLE `infra_file_config` ADD COLUMN `master_group` tinyint GENERATED ALWAYS AS (IF(`master` = 1 AND `deleted` = 0, CAST(`private` AS UNSIGNED), NULL)) STORED COMMENT ''默认配置访问分组'' AFTER `private`',
  'SELECT 1'
);
PREPARE file_config_master_group_stmt FROM @file_config_master_group_sql;
EXECUTE file_config_master_group_stmt;
DEALLOCATE PREPARE file_config_master_group_stmt;

SET @file_config_master_group_index_count = (
  SELECT COUNT(*) FROM `information_schema`.`statistics`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'infra_file_config'
    AND `index_name` = 'uk_infra_file_config_master_group'
);
SET @file_config_master_group_index_sql = IF(
  @file_config_master_group_index_count = 0,
  'ALTER TABLE `infra_file_config` ADD UNIQUE KEY `uk_infra_file_config_master_group` (`master_group`)',
  'SELECT 1'
);
PREPARE file_config_master_group_index_stmt FROM @file_config_master_group_index_sql;
EXECUTE file_config_master_group_index_stmt;
DEALLOCATE PREPARE file_config_master_group_index_stmt;

-- 物流表可以先于或后于本迁移创建；存在时增量增加文件编号列。
SET @waybill_table_count = (
  SELECT COUNT(*) FROM `information_schema`.`tables`
  WHERE `table_schema` = DATABASE() AND `table_name` = 'trade_logistics_waybill'
);
SET @waybill_label_file_id_count = (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_waybill'
    AND `column_name` = 'label_file_id'
);
SET @waybill_label_file_id_sql = IF(
  @waybill_table_count > 0 AND @waybill_label_file_id_count = 0,
  'ALTER TABLE `trade_logistics_waybill` ADD COLUMN `label_file_id` bigint DEFAULT NULL COMMENT ''infra_file.id'' AFTER `active_order_id`',
  'SELECT 1'
);
PREPARE waybill_label_file_id_stmt FROM @waybill_label_file_id_sql;
EXECUTE waybill_label_file_id_stmt;
DEALLOCATE PREPARE waybill_label_file_id_stmt;

SET @print_task_table_count = (
  SELECT COUNT(*) FROM `information_schema`.`tables`
  WHERE `table_schema` = DATABASE() AND `table_name` = 'trade_logistics_print_task'
);
SET @print_task_label_file_id_count = (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_print_task'
    AND `column_name` = 'label_file_id'
);
SET @print_task_label_file_id_sql = IF(
  @print_task_table_count > 0 AND @print_task_label_file_id_count = 0,
  'ALTER TABLE `trade_logistics_print_task` ADD COLUMN `label_file_id` bigint DEFAULT NULL COMMENT ''infra_file.id'' AFTER `format`',
  'SELECT 1'
);
PREPARE print_task_label_file_id_stmt FROM @print_task_label_file_id_sql;
EXECUTE print_task_label_file_id_stmt;
DEALLOCATE PREPARE print_task_label_file_id_stmt;

-- 旧版物流也写入 infra_file；按完全一致的历史 URL 找回文件编号。
-- 两表可能来自不同建表年代并使用不同 collation，因此按二进制比较，避免迁移因 collation 冲突中断。
SET @waybill_label_file_backfill_sql = IF(
  @waybill_table_count > 0,
  'UPDATE `trade_logistics_waybill` w SET w.`label_file_id` = (SELECT CASE WHEN COUNT(*) = 1 THEN MIN(f.`id`) END FROM `infra_file` f WHERE CONVERT(f.`url` USING binary) = CONVERT(w.`label_url` USING binary) AND f.`deleted` = b''0'') WHERE w.`label_file_id` IS NULL AND w.`label_url` IS NOT NULL',
  'SELECT 1'
);
PREPARE waybill_label_file_backfill_stmt FROM @waybill_label_file_backfill_sql;
EXECUTE waybill_label_file_backfill_stmt;
DEALLOCATE PREPARE waybill_label_file_backfill_stmt;

SET @print_task_label_file_backfill_sql = IF(
  @print_task_table_count > 0 AND @waybill_table_count > 0,
  'UPDATE `trade_logistics_print_task` t SET t.`label_file_id` = COALESCE((SELECT w.`label_file_id` FROM `trade_logistics_waybill` w WHERE w.`id` = t.`waybill_id`), (SELECT CASE WHEN COUNT(*) = 1 THEN MIN(f.`id`) END FROM `infra_file` f WHERE CONVERT(f.`url` USING binary) = CONVERT(t.`label_url` USING binary) AND f.`deleted` = b''0'')) WHERE t.`label_file_id` IS NULL',
  'SELECT 1'
);
PREPARE print_task_label_file_backfill_stmt FROM @print_task_label_file_backfill_sql;
EXECUTE print_task_label_file_backfill_stmt;
DEALLOCATE PREPARE print_task_label_file_backfill_stmt;
