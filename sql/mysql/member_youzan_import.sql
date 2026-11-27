CREATE TABLE IF NOT EXISTS `member_youzan_import_grant` (
  `tenant_id` bigint NOT NULL, `merchant_id` bigint NOT NULL, `operator_id` bigint NOT NULL,
  PRIMARY KEY (`tenant_id`,`merchant_id`,`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `member_youzan_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` bigint NOT NULL,
  `merchant_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0预览中 1已确认 2已过期',
  `total_rows` int NOT NULL DEFAULT 0,
  `valid_rows` int NOT NULL DEFAULT 0,
  `invalid_rows` int NOT NULL DEFAULT 0,
  `imported_rows` int NOT NULL DEFAULT 0,
  `skipped_rows` int NOT NULL DEFAULT 0,
  `changed_rows` int NOT NULL DEFAULT 0,
  `expires_at` datetime NOT NULL,
  `encrypted_payload` mediumtext,
  `masked_rows` mediumtext NOT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_youzan_batch_tenant_operator_time` (`tenant_id`,`operator_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='有赞客户积分导入预览批次';

CREATE TABLE IF NOT EXISTS `member_youzan_point_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` bigint NOT NULL,
  `merchant_id` bigint NOT NULL,
  `mapped_level_id` bigint DEFAULT NULL,
  `mobile_hash` char(64) NOT NULL COMMENT '租户内手机号HMAC，用于授权后匹配',
  `encrypted_mobile` varchar(512) NOT NULL COMMENT 'AES加密手机号',
  `legacy_points` int NOT NULL DEFAULT 0 COMMENT '来源旧积分余额快照，不直接计入会员余额',
  `growth_value` int DEFAULT NULL,
  `free_level` varchar(128) DEFAULT NULL COMMENT '原始免费等级，仅供后续显式映射',
  `snapshot_hash` char(64) NOT NULL,
  `restore_status` tinyint NOT NULL DEFAULT 0 COMMENT '0待自愿授权恢复',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_youzan_snapshot_tenant_mobile` (`tenant_id`,`merchant_id`,`mobile_hash`),
  KEY `idx_youzan_snapshot_batch` (`tenant_id`,`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='有赞来源待恢复积分快照';

INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT '有赞客户积分导入','',2,99,m.`parent_id`,'youzan-import','ep:upload','member/youzan-import/index','MemberYouzanImport',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM `system_menu` m WHERE m.`name`='会员管理' AND m.`path`='user' AND m.`type`=2 AND m.`deleted`=b'0'
AND NOT EXISTS (SELECT 1 FROM `system_menu` x WHERE x.`component`='member/youzan-import/index' AND x.`deleted`=b'0');

INSERT INTO `system_menu` (`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT p.`name`,p.`permission`,3,p.`sort`,m.`id`,'','','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM (SELECT '查询有赞客户导入批次' `name`,'member:youzan-import:query' `permission`,1 `sort`
      UNION ALL SELECT '导入有赞客户积分快照','member:youzan-import:import',2) p
JOIN `system_menu` m ON m.`component`='member/youzan-import/index' AND m.`deleted`=b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` x WHERE x.`permission`=p.`permission` AND x.`deleted`=b'0');
