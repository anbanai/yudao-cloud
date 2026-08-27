-- 微信物流助手最小数据模型
-- 月结卡绑定由微信后台管理；本库只保存微信返回的 biz_id 和业务配置。

CREATE TABLE IF NOT EXISTS `trade_wechat_logistics_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `user_type` tinyint NOT NULL DEFAULT 1 COMMENT '微信小程序用户类型',
  `delivery_id` varchar(32) NOT NULL COMMENT '微信快递公司编号，例如 SF',
  `biz_id` varchar(128) NOT NULL COMMENT '微信 getAllAccount 返回的客户编码',
  `service_type` int NOT NULL COMMENT '微信服务类型编号',
  `service_name` varchar(64) NOT NULL COMMENT '微信服务类型名称',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `sender_name` varchar(64) NOT NULL COMMENT '发件人姓名',
  `sender_tel` varchar(32) DEFAULT NULL COMMENT '发件人座机',
  `sender_mobile` varchar(32) NOT NULL COMMENT '发件人手机号',
  `sender_company` varchar(64) DEFAULT NULL COMMENT '发件人公司',
  `sender_post_code` varchar(10) DEFAULT NULL COMMENT '发件人邮编',
  `sender_country` varchar(64) DEFAULT '中国' COMMENT '发件人国家',
  `sender_province` varchar(64) NOT NULL COMMENT '发件人省份',
  `sender_city` varchar(64) NOT NULL COMMENT '发件人城市',
  `sender_area` varchar(64) NOT NULL COMMENT '发件人区县',
  `sender_address` varchar(512) NOT NULL COMMENT '发件人详细地址',
  `default_weight` decimal(10,3) NOT NULL COMMENT '默认重量 kg',
  `default_space_length` decimal(10,2) NOT NULL COMMENT '默认长度 cm',
  `default_space_width` decimal(10,2) NOT NULL COMMENT '默认宽度 cm',
  `default_space_height` decimal(10,2) NOT NULL COMMENT '默认高度 cm',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_user_type` (`tenant_id`,`user_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信物流助手租户配置';

CREATE TABLE IF NOT EXISTS `trade_wechat_logistics_waybill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `order_id` bigint NOT NULL COMMENT '交易订单编号',
  `order_no` varchar(64) NOT NULL COMMENT '交易订单号',
  `wechat_order_id` varchar(512) NOT NULL COMMENT '微信物流订单号',
  `openid` varchar(128) NOT NULL COMMENT '支付用户 openid',
  `delivery_id` varchar(32) NOT NULL COMMENT '微信快递公司编号',
  `biz_id` varchar(128) NOT NULL COMMENT '微信客户编码',
  `waybill_id` varchar(128) DEFAULT NULL COMMENT '微信返回的运单号',
  `status` varchar(16) NOT NULL COMMENT 'CREATING/CREATED/UNKNOWN/FAILED/CANCELLED',
  `print_status` varchar(16) NOT NULL COMMENT 'PENDING/CONFIRMED',
  `wechat_order_status` int DEFAULT NULL COMMENT '微信订单状态',
  `error_code` int DEFAULT NULL COMMENT '微信或快递错误码',
  `error_message` varchar(512) DEFAULT NULL COMMENT '错误信息',
  `waybill_data` json DEFAULT NULL COMMENT '微信面单数据',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后轨迹同步时间',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_order` (`tenant_id`,`order_id`),
  UNIQUE KEY `uk_tenant_wechat_order` (`tenant_id`,`wechat_order_id`),
  UNIQUE KEY `uk_tenant_waybill` (`tenant_id`,`waybill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信物流助手运单';

CREATE TABLE IF NOT EXISTS `trade_wechat_logistics_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `waybill_id` bigint NOT NULL COMMENT '本地运单编号',
  `action_time` datetime NOT NULL COMMENT '物流动作时间',
  `action_type` int DEFAULT NULL COMMENT '微信物流动作类型',
  `action_msg` varchar(512) NOT NULL COMMENT '物流动作描述',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_waybill` (`tenant_id`,`waybill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信物流助手轨迹';

-- 后台菜单与权限（可重复执行，按名称和权限幂等）。
UPDATE `system_menu`
SET `name` = '微信物流配置', `updater` = '1', `update_time` = NOW()
WHERE `component` = 'mall/trade/logistics/wechat/index'
  AND `name` = '微信物流打单' AND `deleted` = b'0';

INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '微信物流配置', '', 2, 66, id, 'logistics/wechat', 'ep:printer', 'mall/trade/logistics/wechat/index', 'TradeWechatLogistics', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM `system_menu` WHERE `name` = '订单中心' AND `path` = 'trade' AND `type` = 1
  AND NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `component` = 'mall/trade/logistics/wechat/index' AND `deleted` = b'0');

INSERT INTO `system_menu` (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT `name`, `permission`, 3, `sort`, (SELECT `id` FROM `system_menu` WHERE `component` = 'mall/trade/logistics/wechat/index' AND `deleted` = b'0' LIMIT 1), '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM (
  SELECT '物流配置查询' AS `name`, 'trade:logistics:config:query' AS `permission`, 1 AS `sort`
  UNION ALL SELECT '物流配置保存', 'trade:logistics:config:update', 2
  UNION ALL SELECT '物流账号查询', 'trade:logistics:account:query', 3
  UNION ALL SELECT '物流运单查询', 'trade:logistics:waybill:query', 4
  UNION ALL SELECT '物流运单创建', 'trade:logistics:waybill:create', 5
  UNION ALL SELECT '确认打印并发货', 'trade:logistics:waybill:confirm-print', 6
  UNION ALL SELECT '取消物流运单', 'trade:logistics:waybill:cancel', 7
  UNION ALL SELECT '物流轨迹查询', 'trade:logistics:trace:query', 8
  UNION ALL SELECT '同步物流轨迹', 'trade:logistics:trace:sync', 9
  UNION ALL SELECT '打印员查询', 'trade:logistics:printer:query', 10
  UNION ALL SELECT '打印员绑定', 'trade:logistics:printer:update', 11
) p
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` m WHERE m.`permission` = p.`permission` AND m.`deleted` = b'0');
