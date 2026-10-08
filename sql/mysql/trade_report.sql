-- 交易备货单、对账单历史快照
CREATE TABLE IF NOT EXISTS `trade_report_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `report_type` varchar(32) NOT NULL COMMENT '报表类型 picking/reconciliation',
  `filters_json` text NOT NULL COMMENT '筛选条件快照',
  `order_count` bigint NOT NULL DEFAULT 0 COMMENT '订单数',
  `product_type_count` int NOT NULL DEFAULT 0 COMMENT '商品种类数',
  `product_count` int NOT NULL DEFAULT 0 COMMENT '商品数量',
  `amount` bigint NOT NULL DEFAULT 0 COMMENT '金额，单位：分',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_report_history_type_time` (`tenant_id`,`report_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易报表历史快照';

CREATE TABLE IF NOT EXISTS `trade_report_history_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `history_id` bigint NOT NULL COMMENT '历史快照编号',
  `spu_id` bigint DEFAULT NULL COMMENT 'SPU 编号',
  `sku_id` bigint DEFAULT NULL COMMENT 'SKU 编号',
  `product_name` varchar(255) NOT NULL DEFAULT '' COMMENT '商品名称快照',
  `pic_url` varchar(1024) DEFAULT NULL COMMENT '商品图片快照',
  `bar_code` varchar(128) DEFAULT NULL COMMENT 'SKU 条码快照',
  `properties` varchar(1024) DEFAULT NULL COMMENT '销售属性快照',
  `quantity` int NOT NULL DEFAULT 0 COMMENT '数量',
  `amount` bigint NOT NULL DEFAULT 0 COMMENT '金额，单位：分',
  `order_count` int NOT NULL DEFAULT 0 COMMENT '涉及订单数',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_report_history_detail_history` (`tenant_id`,`history_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易报表历史明细快照';

-- 交易中心报表菜单与权限（可重复执行）
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '备货单','',2,2,m.id,'picking','ep:goods','mall/trade/report/index','TradeReportPicking',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM `system_menu` m WHERE m.`name`='订单中心' AND m.`path`='trade' AND m.`type`=1 AND m.`deleted`=b'0'
AND NOT EXISTS (SELECT 1 FROM `system_menu` x WHERE x.`component`='mall/trade/report/index' AND x.`path`='picking' AND x.`deleted`=b'0');
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '对账单','',2,3,m.id,'reconciliation','ep:document-checked','mall/trade/report/index','TradeReportReconciliation',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM `system_menu` m WHERE m.`name`='订单中心' AND m.`path`='trade' AND m.`type`=1 AND m.`deleted`=b'0'
AND NOT EXISTS (SELECT 1 FROM `system_menu` x WHERE x.`component`='mall/trade/report/index' AND x.`path`='reconciliation' AND x.`deleted`=b'0');
INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT p.name,p.permission,3,p.sort,m.id,'','','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM (
  SELECT '备货单查询' name,'trade:report:picking:query' permission,1 sort UNION ALL
  SELECT '备货单保存','trade:report:picking:save',2 UNION ALL
  SELECT '备货单导出','trade:report:picking:export',3 UNION ALL
  SELECT '对账单查询','trade:report:reconciliation:query',4 UNION ALL
  SELECT '对账单保存','trade:report:reconciliation:save',5 UNION ALL
  SELECT '对账单导出','trade:report:reconciliation:export',6
) p JOIN `system_menu` m ON m.`name` IN ('备货单','对账单') AND m.`deleted`=b'0'
WHERE ((p.permission LIKE 'trade:report:picking:%' AND m.`name`='备货单')
   OR (p.permission LIKE 'trade:report:reconciliation:%' AND m.`name`='对账单'))
AND NOT EXISTS (SELECT 1 FROM `system_menu` x WHERE x.`permission`=p.permission AND x.`deleted`=b'0');
