-- 售后逆向物流增量迁移。可重复执行，适用于已部署旧版本数据库。
CREATE TABLE IF NOT EXISTS `trade_after_sale_return_shipment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '逆向物流编号',
  `after_sale_id` bigint NOT NULL COMMENT '售后单编号',
  `account_id` bigint DEFAULT NULL COMMENT '物流账号编号',
  `provider` varchar(32) NOT NULL COMMENT '服务商',
  `provider_order_no` varchar(64) DEFAULT NULL COMMENT '服务商订单号',
  `waybill_no` varchar(64) DEFAULT NULL COMMENT '运单号',
  `status` varchar(32) NOT NULL COMMENT '逆向物流状态',
  `idempotency_key` varchar(64) NOT NULL COMMENT '幂等请求号',
  `address_id` bigint DEFAULT NULL COMMENT '用户地址编号',
  `pickup_name` varchar(64) DEFAULT NULL COMMENT '取件联系人快照',
  `pickup_mobile` varchar(32) DEFAULT NULL COMMENT '取件手机号快照',
  `pickup_area_id` int DEFAULT NULL COMMENT '取件地区编号',
  `pickup_province` varchar(64) DEFAULT NULL COMMENT '取件省份快照',
  `pickup_city` varchar(64) DEFAULT NULL COMMENT '取件城市快照',
  `pickup_district` varchar(64) DEFAULT NULL COMMENT '取件区县快照',
  `pickup_address` varchar(255) DEFAULT NULL COMMENT '取件详细地址快照',
  `warehouse_name` varchar(64) DEFAULT NULL COMMENT '退货仓联系人快照',
  `warehouse_phone` varchar(32) DEFAULT NULL COMMENT '退货仓电话快照',
  `warehouse_province` varchar(64) DEFAULT NULL COMMENT '退货仓省份快照',
  `warehouse_city` varchar(64) DEFAULT NULL COMMENT '退货仓城市快照',
  `warehouse_district` varchar(64) DEFAULT NULL COMMENT '退货仓区县快照',
  `warehouse_address` varchar(255) DEFAULT NULL COMMENT '退货仓地址快照',
  `appointment_time` datetime DEFAULT NULL,
  `package_weight` decimal(10,2) DEFAULT NULL,
  `fee_payer` varchar(32) DEFAULT NULL,
  `estimated_fee` int DEFAULT NULL COMMENT '预计费用，单位：分',
  `actual_fee` int DEFAULT NULL COMMENT '实际费用，单位：分',
  `refund_deducted_fee` int DEFAULT NULL COMMENT '已从退款扣除的实际费用，单位：分',
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `provider_response` text,
  `last_sync_time` datetime DEFAULT NULL,
  `cancelled_time` datetime DEFAULT NULL,
  `delivered_time` datetime DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_after_sale_id` (`after_sale_id`),
  UNIQUE KEY `uk_tenant_idempotency_key` (`tenant_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='售后逆向物流单';

CREATE TABLE IF NOT EXISTS `trade_after_sale_return_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `return_shipment_id` bigint NOT NULL,
  `provider` varchar(32) NOT NULL, `status` varchar(32) NOT NULL,
  `description` varchar(500) DEFAULT NULL, `location` varchar(255) DEFAULT NULL,
  `occurred_time` datetime DEFAULT NULL, `event_id` varchar(128) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_shipment_event` (`return_shipment_id`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='售后逆向物流轨迹';

SET @return_shipment_refund_fee_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_after_sale_return_shipment'
    AND COLUMN_NAME = 'refund_deducted_fee'
);
SET @return_shipment_refund_fee_sql := IF(@return_shipment_refund_fee_exists = 0,
  'ALTER TABLE `trade_after_sale_return_shipment` ADD COLUMN `refund_deducted_fee` int DEFAULT NULL COMMENT ''已从退款扣除的实际费用，单位：分'' AFTER `actual_fee`',
  'SELECT 1');
PREPARE return_shipment_refund_fee_stmt FROM @return_shipment_refund_fee_sql;
EXECUTE return_shipment_refund_fee_stmt;
DEALLOCATE PREPARE return_shipment_refund_fee_stmt;

-- 将旧版本全局幂等索引升级为租户级幂等约束。
SET @return_shipment_old_idempotency_index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_after_sale_return_shipment'
    AND INDEX_NAME = 'uk_idempotency_key'
);
SET @return_shipment_drop_old_idempotency_sql := IF(@return_shipment_old_idempotency_index_exists > 0,
  'ALTER TABLE `trade_after_sale_return_shipment` DROP INDEX `uk_idempotency_key`',
  'SELECT 1');
PREPARE return_shipment_drop_old_idempotency_stmt FROM @return_shipment_drop_old_idempotency_sql;
EXECUTE return_shipment_drop_old_idempotency_stmt;
DEALLOCATE PREPARE return_shipment_drop_old_idempotency_stmt;

SET @return_shipment_tenant_idempotency_index_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_after_sale_return_shipment'
    AND INDEX_NAME = 'uk_tenant_idempotency_key'
);
SET @return_shipment_add_tenant_idempotency_sql := IF(@return_shipment_tenant_idempotency_index_exists = 0,
  'ALTER TABLE `trade_after_sale_return_shipment` ADD UNIQUE KEY `uk_tenant_idempotency_key` (`tenant_id`,`idempotency_key`)',
  'SELECT 1');
PREPARE return_shipment_add_tenant_idempotency_stmt FROM @return_shipment_add_tenant_idempotency_sql;
EXECUTE return_shipment_add_tenant_idempotency_stmt;
DEALLOCATE PREPARE return_shipment_add_tenant_idempotency_stmt;
