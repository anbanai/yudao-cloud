-- 顺丰直连 + PrintBridge 打单（MySQL 8）
-- 不删除、不修改 trade_wechat_logistics_* 历史表。

CREATE TABLE IF NOT EXISTS `trade_logistics_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `name` varchar(64) NOT NULL,
  `logistics_id` bigint NOT NULL COMMENT 'trade_delivery_express.id（顺丰）',
  `endpoint` varchar(255) NOT NULL,
  `partner_id` varchar(512) NOT NULL COMMENT '加密保存',
  `check_word` varchar(512) NOT NULL COMMENT '加密保存',
  `monthly_card` varchar(512) NOT NULL COMMENT '加密保存',
  `service_code` varchar(32) NOT NULL COMMENT '顺丰产品类型编号',
  `template_code` varchar(64) NOT NULL COMMENT '顺丰云打印模板代码（必须与纸张规格匹配）',
  `sender_name` varchar(64) NOT NULL,
  `sender_phone` varchar(32) NOT NULL,
  `sender_province` varchar(64) NOT NULL,
  `sender_city` varchar(64) NOT NULL,
  `sender_district` varchar(64) DEFAULT NULL,
  `sender_address` varchar(512) NOT NULL,
  `default_weight_kg` decimal(10,3) NOT NULL,
  `paper_width_mm` int NOT NULL DEFAULT 76,
  `paper_height_mm` int NOT NULL DEFAULT 130,
  `dpi` int NOT NULL DEFAULT 203,
  `default_flag` bit(1) NOT NULL DEFAULT b'0',
  `status` tinyint NOT NULL DEFAULT 0,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_logistics_account_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顺丰开放平台账号';

CREATE TABLE IF NOT EXISTS `trade_logistics_waybill` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `order_id` bigint NOT NULL,
  `order_no` varchar(64) NOT NULL,
  `account_id` bigint NOT NULL,
  `logistics_id` bigint NOT NULL,
  `requested_device_id` bigint DEFAULT NULL,
  `provider_order_no` varchar(128) NOT NULL,
  `waybill_no` varchar(64) DEFAULT NULL,
  `status` varchar(16) NOT NULL COMMENT 'CREATING/CREATED/UNKNOWN/FAILED/CANCELLING/CANCEL_UNKNOWN/CANCELLED',
  `active_order_id` bigint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' AND `status` <> 'CANCELLED' THEN `order_id` ELSE NULL END) STORED,
  `label_file_id` bigint DEFAULT NULL COMMENT 'infra_file.id',
  `label_url` varchar(1024) DEFAULT NULL,
  `label_content_type` varchar(32) DEFAULT NULL,
  `label_checksum` varchar(64) DEFAULT NULL,
  `label_size` bigint DEFAULT NULL,
  `template_code` varchar(64) DEFAULT NULL,
  `paper_width_mm` int DEFAULT NULL,
  `paper_height_mm` int DEFAULT NULL,
  `dpi` int DEFAULT NULL,
  `delivery_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DELIVERED/CONFLICT',
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `provider_response` longtext,
  `last_sync_time` datetime DEFAULT NULL,
  `delivered_time` datetime DEFAULT NULL,
  `cancelled_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_logistics_active_order` (`tenant_id`,`active_order_id`),
  UNIQUE KEY `uk_logistics_provider_order` (`tenant_id`,`provider_order_no`),
  UNIQUE KEY `uk_logistics_waybill_no` (`tenant_id`,`waybill_no`),
  KEY `idx_logistics_waybill_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顺丰运单';

CREATE TABLE IF NOT EXISTS `trade_logistics_print_device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `device_code` varchar(64) NOT NULL,
  `device_name` varchar(128) NOT NULL,
  `printer_name` varchar(255) DEFAULT NULL COMMENT 'Windows 打印机名称',
  `token_hash` char(64) NOT NULL,
  `enrollment_key` varchar(32) DEFAULT NULL COMMENT '首次连接占位键；连接成功后清空',
  `default_flag` bit(1) NOT NULL DEFAULT b'0',
  `status` tinyint NOT NULL DEFAULT 0,
  `version` varchar(32) DEFAULT NULL,
  `last_poll_time` datetime DEFAULT NULL,
  `token_created_time` datetime NOT NULL,
  `disabled_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_logistics_device_code` (`tenant_id`,`device_code`,`deleted`),
  UNIQUE KEY `uk_logistics_device_token` (`token_hash`),
  UNIQUE KEY `uk_logistics_device_enrollment` (`tenant_id`,`enrollment_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PrintBridge 设备';

-- 兼容已执行旧版脚本的数据库。
SET @logistics_printer_name_count = (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_print_device'
    AND `column_name` = 'printer_name'
);
SET @logistics_printer_name_sql = IF(
  @logistics_printer_name_count = 0,
  'ALTER TABLE `trade_logistics_print_device` ADD COLUMN `printer_name` varchar(255) DEFAULT NULL COMMENT ''Windows 打印机名称'' AFTER `device_name`',
  'SELECT 1'
);
PREPARE logistics_printer_name_stmt FROM @logistics_printer_name_sql;
EXECUTE logistics_printer_name_stmt;
DEALLOCATE PREPARE logistics_printer_name_stmt;

SET @logistics_enrollment_key_count = (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_print_device'
    AND `column_name` = 'enrollment_key'
);
SET @logistics_enrollment_key_sql = IF(
  @logistics_enrollment_key_count = 0,
  'ALTER TABLE `trade_logistics_print_device` ADD COLUMN `enrollment_key` varchar(32) DEFAULT NULL COMMENT ''首次连接占位键；连接成功后清空'' AFTER `token_hash`',
  'SELECT 1'
);
PREPARE logistics_enrollment_key_stmt FROM @logistics_enrollment_key_sql;
EXECUTE logistics_enrollment_key_stmt;
DEALLOCATE PREPARE logistics_enrollment_key_stmt;

SET @logistics_enrollment_index_count = (
  SELECT COUNT(*) FROM `information_schema`.`statistics`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_print_device'
    AND `index_name` = 'uk_logistics_device_enrollment'
);
SET @logistics_enrollment_index_sql = IF(
  @logistics_enrollment_index_count = 0,
  'ALTER TABLE `trade_logistics_print_device` ADD UNIQUE KEY `uk_logistics_device_enrollment` (`tenant_id`,`enrollment_key`)',
  'SELECT 1'
);
PREPARE logistics_enrollment_index_stmt FROM @logistics_enrollment_index_sql;
EXECUTE logistics_enrollment_index_stmt;
DEALLOCATE PREPARE logistics_enrollment_index_stmt;

CREATE TABLE IF NOT EXISTS `trade_logistics_print_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `request_id` varchar(64) NOT NULL,
  `job_id` varchar(64) NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `waybill_id` bigint DEFAULT NULL,
  `device_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL COMMENT 'PENDING/DISPATCHED/ACCEPTED/SUCCESS/FAILED/UNKNOWN/CANCELLED',
  `format` varchar(16) NOT NULL DEFAULT 'image',
  `label_file_id` bigint DEFAULT NULL COMMENT 'infra_file.id',
  `label_url` varchar(1024) NOT NULL,
  `checksum` varchar(64) NOT NULL,
  `paper_width_mm` int NOT NULL DEFAULT 76,
  `paper_height_mm` int NOT NULL DEFAULT 130,
  `dpi` int NOT NULL DEFAULT 203,
  `copies` int NOT NULL DEFAULT 1,
  `test_flag` bit(1) NOT NULL DEFAULT b'0',
  `lease_expire_time` datetime DEFAULT NULL,
  `dispatched_time` datetime DEFAULT NULL,
  `accepted_time` datetime DEFAULT NULL,
  `completed_time` datetime DEFAULT NULL,
  `last_error` varchar(1024) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_logistics_request_id` (`request_id`),
  UNIQUE KEY `uk_logistics_job_id` (`job_id`),
  KEY `idx_logistics_task_pull` (`tenant_id`,`device_id`,`status`,`lease_expire_time`),
  KEY `idx_logistics_task_waybill` (`tenant_id`,`waybill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持久化打印任务';

-- 兼容已经执行过旧版脚本的数据库：保存文件编号后可始终按原存储配置签名。
SET @logistics_waybill_label_file_id_count = (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_waybill'
    AND `column_name` = 'label_file_id'
);
SET @logistics_waybill_label_file_id_sql = IF(
  @logistics_waybill_label_file_id_count = 0,
  'ALTER TABLE `trade_logistics_waybill` ADD COLUMN `label_file_id` bigint DEFAULT NULL COMMENT ''infra_file.id'' AFTER `active_order_id`',
  'SELECT 1'
);
PREPARE logistics_waybill_label_file_id_stmt FROM @logistics_waybill_label_file_id_sql;
EXECUTE logistics_waybill_label_file_id_stmt;
DEALLOCATE PREPARE logistics_waybill_label_file_id_stmt;

SET @logistics_task_label_file_id_count = (
  SELECT COUNT(*) FROM `information_schema`.`columns`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_print_task'
    AND `column_name` = 'label_file_id'
);
SET @logistics_task_label_file_id_sql = IF(
  @logistics_task_label_file_id_count = 0,
  'ALTER TABLE `trade_logistics_print_task` ADD COLUMN `label_file_id` bigint DEFAULT NULL COMMENT ''infra_file.id'' AFTER `format`',
  'SELECT 1'
);
PREPARE logistics_task_label_file_id_stmt FROM @logistics_task_label_file_id_sql;
EXECUTE logistics_task_label_file_id_stmt;
DEALLOCATE PREPARE logistics_task_label_file_id_stmt;

UPDATE `trade_logistics_waybill` w
SET w.`label_file_id` = (
  SELECT CASE WHEN COUNT(*) = 1 THEN MIN(f.`id`) END FROM `infra_file` f
  WHERE f.`url` = w.`label_url` AND f.`deleted` = b'0'
)
WHERE w.`label_file_id` IS NULL AND w.`label_url` IS NOT NULL;

UPDATE `trade_logistics_print_task` t
SET t.`label_file_id` = COALESCE(
  (SELECT w.`label_file_id` FROM `trade_logistics_waybill` w WHERE w.`id` = t.`waybill_id`),
  (SELECT CASE WHEN COUNT(*) = 1 THEN MIN(f.`id`) END FROM `infra_file` f WHERE f.`url` = t.`label_url` AND f.`deleted` = b'0')
)
WHERE t.`label_file_id` IS NULL;

CREATE TABLE IF NOT EXISTS `trade_logistics_print_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `event_id` varchar(128) NOT NULL,
  `task_id` bigint NOT NULL,
  `device_id` bigint NOT NULL,
  `job_id` varchar(64) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL,
  `message` varchar(1024) DEFAULT NULL,
  `event_time` datetime NOT NULL,
  `raw_payload` longtext,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_logistics_event` (`tenant_id`,`event_id`),
  KEY `idx_logistics_event_task` (`tenant_id`,`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PrintBridge 回执事件';

-- 兼容已经执行过旧版脚本的数据库：将全局 event_id 唯一约束升级为租户内唯一。
SET @logistics_event_index_columns = (
  SELECT GROUP_CONCAT(`column_name` ORDER BY `seq_in_index` SEPARATOR ',')
  FROM `information_schema`.`statistics`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_print_event'
    AND `index_name` = 'uk_logistics_event'
);
SET @logistics_event_index_sql = IF(
  @logistics_event_index_columns = 'event_id',
  'ALTER TABLE `trade_logistics_print_event` DROP INDEX `uk_logistics_event`, ADD UNIQUE KEY `uk_logistics_event` (`tenant_id`,`event_id`)',
  'SELECT 1'
);
PREPARE logistics_event_index_stmt FROM @logistics_event_index_sql;
EXECUTE logistics_event_index_stmt;
DEALLOCATE PREPARE logistics_event_index_stmt;

CREATE TABLE IF NOT EXISTS `trade_logistics_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `waybill_id` bigint NOT NULL,
  `provider_event_id` varchar(128) DEFAULT NULL,
  `status` varchar(32) DEFAULT NULL,
  `content` varchar(1024) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `operate_time` datetime NOT NULL,
  `raw_data` longtext,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_logistics_trace_event` (`tenant_id`,`waybill_id`,`provider_event_id`),
  KEY `idx_logistics_trace_waybill` (`tenant_id`,`waybill_id`,`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顺丰物流轨迹';

-- 兼容已经执行过旧版脚本的数据库：只修改新记录默认值，不更新已有账号和打印任务。
ALTER TABLE `trade_logistics_account`
  MODIFY COLUMN `template_code` varchar(64) NOT NULL COMMENT '顺丰云打印模板代码（必须与纸张规格匹配）',
  MODIFY COLUMN `paper_width_mm` int NOT NULL DEFAULT 76,
  MODIFY COLUMN `paper_height_mm` int NOT NULL DEFAULT 130;
ALTER TABLE `trade_logistics_print_task`
  MODIFY COLUMN `paper_width_mm` int NOT NULL DEFAULT 76,
  MODIFY COLUMN `paper_height_mm` int NOT NULL DEFAULT 130;

SET @logistics_trace_event_index_count = (
  SELECT COUNT(*) FROM `information_schema`.`statistics`
  WHERE `table_schema` = DATABASE()
    AND `table_name` = 'trade_logistics_trace'
    AND `index_name` = 'uk_logistics_trace_event'
);
-- 旧版本曾采用“删除后全量重建”轨迹；建唯一键前只清理同一顺丰事件的重复行。
DELETE duplicate_trace
FROM `trade_logistics_trace` duplicate_trace
JOIN `trade_logistics_trace` retained_trace
  ON retained_trace.`tenant_id` = duplicate_trace.`tenant_id`
 AND retained_trace.`waybill_id` = duplicate_trace.`waybill_id`
 AND retained_trace.`provider_event_id` = duplicate_trace.`provider_event_id`
 AND duplicate_trace.`provider_event_id` IS NOT NULL
 AND (retained_trace.`deleted` < duplicate_trace.`deleted`
   OR (retained_trace.`deleted` = duplicate_trace.`deleted` AND retained_trace.`id` < duplicate_trace.`id`))
WHERE @logistics_trace_event_index_count = 0;
SET @logistics_trace_event_index_sql = IF(
  @logistics_trace_event_index_count = 0,
  'ALTER TABLE `trade_logistics_trace` ADD UNIQUE KEY `uk_logistics_trace_event` (`tenant_id`,`waybill_id`,`provider_event_id`)',
  'SELECT 1'
);
PREPARE logistics_trace_event_index_stmt FROM @logistics_trace_event_index_sql;
EXECUTE logistics_trace_event_index_stmt;
DEALLOCATE PREPARE logistics_trace_event_index_stmt;

-- 物流打单目录
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '物流打单','',1,67,id,'logistics','ep:printer','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM `system_menu` WHERE `name`='订单中心' AND `path`='trade' AND `type`=1
AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `path`='logistics' AND `name`='物流打单' AND `deleted`=b'0');

UPDATE `system_menu`
SET `name`='微信物流历史',
    `path`='wechat-history',
    `parent_id`=(SELECT m.id FROM (SELECT id FROM `system_menu` WHERE `name`='物流打单' AND `path`='logistics' AND `deleted`=b'0' LIMIT 1) m),
    `updater`='1', `update_time`=NOW()
WHERE `component`='mall/trade/logistics/wechat/index' AND `deleted`=b'0';

-- 兼容旧版顺丰权限命名，并保留原菜单 ID 与角色授权关系；微信物流权限不调整。
UPDATE `system_menu` SET `permission`='trade:logistics:sf-waybill:query', `updater`='1', `update_time`=NOW()
WHERE `name`='顺丰运单查询' AND `permission`='trade:logistics:waybill:query' AND `deleted`=b'0';
UPDATE `system_menu` SET `permission`='trade:logistics:sf-waybill:create', `updater`='1', `update_time`=NOW()
WHERE `name`='创建顺丰运单' AND `permission`='trade:logistics:waybill:create' AND `deleted`=b'0';
UPDATE `system_menu` SET `permission`='trade:logistics:sf-waybill:cancel', `updater`='1', `update_time`=NOW()
WHERE `name`='取消顺丰运单' AND `permission`='trade:logistics:waybill:cancel' AND `deleted`=b'0';
UPDATE `system_menu` SET `permission`='trade:logistics:sf-waybill:reprint', `updater`='1', `update_time`=NOW()
WHERE `name`='人工重打' AND `permission`='trade:logistics:waybill:reprint' AND `deleted`=b'0';
UPDATE `system_menu` SET `permission`='trade:logistics:sf-trace:query', `updater`='1', `update_time`=NOW()
WHERE `name`='顺丰轨迹查询' AND `permission`='trade:logistics:trace:query' AND `deleted`=b'0';
UPDATE `system_menu` SET `permission`='trade:logistics:sf-trace:sync', `updater`='1', `update_time`=NOW()
WHERE `name`='顺丰轨迹同步' AND `permission`='trade:logistics:trace:sync' AND `deleted`=b'0';

INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT p.name,'',2,p.sort,m.id,p.path,p.icon,p.component,p.component_name,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM (SELECT '待发货工作台' name,1 sort,'pending' path,'ep:box' icon,'mall/trade/logistics/sf/pending/index' component,'TradeSfLogisticsPending' component_name
      UNION ALL SELECT '顺丰账号',2,'accounts','ep:key','mall/trade/logistics/sf/accounts/index','TradeSfLogisticsAccounts'
      UNION ALL SELECT '打印设备',3,'devices','ep:monitor','mall/trade/logistics/sf/devices/index','TradeSfLogisticsDevices'
      UNION ALL SELECT '运单管理',4,'waybills','ep:tickets','mall/trade/logistics/sf/waybills/index','TradeSfLogisticsWaybills'
      UNION ALL SELECT '打印任务',5,'tasks','ep:list','mall/trade/logistics/sf/tasks/index','TradeSfLogisticsTasks'
      UNION ALL SELECT '微信物流历史',6,'wechat-history','ep:clock','mall/trade/logistics/wechat/index','TradeWechatLogistics') p
JOIN `system_menu` m ON m.name='物流打单' AND m.path='logistics' AND m.deleted=b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` x WHERE x.component=p.component AND x.deleted=b'0');

INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT p.name,p.permission,3,p.sort,m.id,'','','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM (SELECT '顺丰账号查询' name,'trade:logistics:sf-account:query' permission,1 sort
      UNION ALL SELECT '顺丰账号维护','trade:logistics:sf-account:update',2
      UNION ALL SELECT '打印设备查询','trade:logistics:device:query',3
      UNION ALL SELECT '打印设备维护','trade:logistics:device:update',4
      UNION ALL SELECT '本机打印诊断','trade:logistics:diagnostics',5
      UNION ALL SELECT '顺丰运单查询','trade:logistics:sf-waybill:query',6
      UNION ALL SELECT '创建顺丰运单','trade:logistics:sf-waybill:create',7
      UNION ALL SELECT '取消顺丰运单','trade:logistics:sf-waybill:cancel',8
      UNION ALL SELECT '人工重打','trade:logistics:sf-waybill:reprint',9
      UNION ALL SELECT '打印任务查询','trade:logistics:print-task:query',10
      UNION ALL SELECT '顺丰轨迹查询','trade:logistics:sf-trace:query',11
      UNION ALL SELECT '顺丰轨迹同步','trade:logistics:sf-trace:sync',12) p
JOIN `system_menu` m ON m.name='物流打单' AND m.path='logistics' AND m.deleted=b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` x WHERE x.permission=p.permission AND x.deleted=b'0');
