-- 多商家商城一期：商家、商品归属及商家履约订单
CREATE TABLE IF NOT EXISTS `product_merchant` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商家编号',
    `name` varchar(128) NOT NULL COMMENT '商家名称',
    `store_name` varchar(128) DEFAULT NULL COMMENT '店铺名称',
    `logo_url` varchar(512) DEFAULT NULL COMMENT '店铺 logo',
    `description` varchar(512) DEFAULT NULL COMMENT '店铺简介',
    `company_name` varchar(255) DEFAULT NULL COMMENT '企业主体名称',
    `credit_code` varchar(2048) DEFAULT NULL COMMENT '统一社会信用代码（加密）',
    `legal_person` varchar(2048) DEFAULT NULL COMMENT '法人（加密）',
    `contact_name` varchar(2048) DEFAULT NULL COMMENT '联系人（加密）',
    `contact_mobile` varchar(2048) DEFAULT NULL COMMENT '联系手机（加密）',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '入驻状态',
    `approved_revision` int DEFAULT NULL COMMENT '当前生效申请版本号',
    `reviewer_id` bigint DEFAULT NULL COMMENT '审核人',
    `review_time` datetime DEFAULT NULL COMMENT '审核时间',
    `reject_reason` varchar(512) DEFAULT NULL COMMENT '驳回原因',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) COMMENT='平台商家';

CREATE TABLE IF NOT EXISTS `product_merchant_qualification` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资质编号',
    `merchant_id` bigint NOT NULL COMMENT '商家编号',
    `type` varchar(64) NOT NULL COMMENT '资质类型',
    `certificate_no` varchar(2048) DEFAULT NULL COMMENT '证件编号（加密）',
    `file_url` text NOT NULL COMMENT '证件文件地址（加密）',
    `valid_start` date DEFAULT NULL COMMENT '有效期开始',
    `valid_end` date DEFAULT NULL COMMENT '有效期结束',
    `status` tinyint NOT NULL DEFAULT 10 COMMENT '审核状态',
    `reviewer_id` bigint DEFAULT NULL COMMENT '审核人',
    `review_reason` varchar(512) DEFAULT NULL COMMENT '审核意见',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) COMMENT='商家资质';

CREATE TABLE IF NOT EXISTS `system_merchant_user` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商家账号ID',
    `merchant_id` bigint DEFAULT NULL COMMENT '绑定商家编号',
    `username` varchar(30) NOT NULL COMMENT '账号',
    `password` varchar(100) NOT NULL COMMENT '密码',
    `nickname` varchar(30) NOT NULL COMMENT '昵称',
    `email` varchar(50) DEFAULT '' COMMENT '邮箱',
    `mobile` varchar(30) DEFAULT '' COMMENT '手机号',
    `sex` tinyint DEFAULT 0 COMMENT '性别',
    `avatar` varchar(512) DEFAULT '' COMMENT '头像',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '账号状态（0正常 1停用）',
    `login_ip` varchar(50) DEFAULT '' COMMENT '最后登录IP',
    `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_merchant_user_username` (`username`),
    KEY `idx_system_merchant_user_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家工作台账号信息表';

-- 加密字段使用 AES Base64 存储，扩宽列以容纳密文。
ALTER TABLE `product_merchant`
    MODIFY COLUMN `credit_code` varchar(2048) DEFAULT NULL COMMENT '统一社会信用代码（加密）',
    MODIFY COLUMN `legal_person` varchar(2048) DEFAULT NULL COMMENT '法人（加密）',
    MODIFY COLUMN `contact_name` varchar(2048) DEFAULT NULL COMMENT '联系人（加密）',
    MODIFY COLUMN `contact_mobile` varchar(2048) DEFAULT NULL COMMENT '联系手机（加密）';
ALTER TABLE `product_merchant_qualification`
    MODIFY COLUMN `certificate_no` varchar(2048) DEFAULT NULL COMMENT '证件编号（加密）',
    MODIFY COLUMN `file_url` text NOT NULL COMMENT '证件文件地址（加密）';

DROP PROCEDURE IF EXISTS `tmp_add_column_if_missing`;
DELIMITER $$
CREATE PROCEDURE `tmp_add_column_if_missing`(IN p_table varchar(64), IN p_column varchar(64), IN p_definition text)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column
    ) THEN
        SET @merchant_add_column_sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE merchant_add_column_stmt FROM @merchant_add_column_sql;
        EXECUTE merchant_add_column_stmt;
        DEALLOCATE PREPARE merchant_add_column_stmt;
    END IF;
END$$
DELIMITER ;

CALL `tmp_add_column_if_missing`('product_brand', 'merchant_id', 'bigint DEFAULT NULL COMMENT ''品牌默认归属商家编号''');
CALL `tmp_add_column_if_missing`('product_spu', 'merchant_id', 'bigint DEFAULT NULL COMMENT ''商品销售商家编号''');
CALL `tmp_add_column_if_missing`('trade_order_item', 'sub_order_id', 'bigint DEFAULT NULL COMMENT ''商家履约订单编号''');
CALL `tmp_add_column_if_missing`('trade_order_item', 'merchant_id', 'bigint DEFAULT NULL COMMENT ''商家编号快照''');
CALL `tmp_add_column_if_missing`('trade_order_item', 'merchant_name', 'varchar(128) DEFAULT NULL COMMENT ''商家名称快照''');
CALL `tmp_add_column_if_missing`('trade_logistics_waybill', 'sub_order_id', 'bigint DEFAULT NULL COMMENT ''商家履约订单编号'' AFTER `order_id`');
SET @drop_merchant_waybill_index = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE `trade_logistics_waybill` DROP INDEX `uk_logistics_active_order`',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'trade_logistics_waybill'
      AND index_name = 'uk_logistics_active_order'
);
PREPARE drop_merchant_waybill_index_stmt FROM @drop_merchant_waybill_index;
EXECUTE drop_merchant_waybill_index_stmt;
DEALLOCATE PREPARE drop_merchant_waybill_index_stmt;
SET @add_merchant_waybill_index = (
    SELECT IF(
              (SELECT COUNT(*) FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'trade_logistics_waybill') = 1
              AND COUNT(*) = 0,
              'ALTER TABLE `trade_logistics_waybill` ADD INDEX `idx_logistics_active_order` (`tenant_id`, `active_order_id`)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'trade_logistics_waybill'
      AND index_name = 'idx_logistics_active_order'
);
PREPARE add_merchant_waybill_index_stmt FROM @add_merchant_waybill_index;
EXECUTE add_merchant_waybill_index_stmt;
DEALLOCATE PREPARE add_merchant_waybill_index_stmt;

CREATE TABLE IF NOT EXISTS `trade_order_sub` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商家订单编号',
    `order_id` bigint NOT NULL COMMENT '平台主订单编号',
    `no` varchar(32) NOT NULL COMMENT '商家订单号',
    `user_id` bigint NOT NULL COMMENT '用户编号',
    `merchant_id` bigint NOT NULL COMMENT '商家编号',
    `merchant_name` varchar(128) DEFAULT NULL COMMENT '商家名称快照',
    `status` int NOT NULL COMMENT '订单状态',
    `product_count` int NOT NULL DEFAULT 0 COMMENT '商品数量',
    `total_price` int NOT NULL DEFAULT 0 COMMENT '商品原价',
    `discount_price` int NOT NULL DEFAULT 0 COMMENT '优惠金额',
    `coupon_price` int NOT NULL DEFAULT 0 COMMENT '优惠券抵扣',
    `point_price` int NOT NULL DEFAULT 0 COMMENT '积分抵扣',
    `vip_price` int NOT NULL DEFAULT 0 COMMENT '会员折扣',
    `adjust_price` int NOT NULL DEFAULT 0 COMMENT '订单调价',
    `delivery_price` int NOT NULL DEFAULT 0 COMMENT '运费',
    `pay_price` int NOT NULL DEFAULT 0 COMMENT '应付金额',
    `delivery_type` int DEFAULT NULL COMMENT '配送方式',
    `logistics_id` bigint DEFAULT NULL COMMENT '物流公司编号',
    `logistics_no` varchar(64) DEFAULT NULL COMMENT '物流单号',
    `delivery_time` datetime DEFAULT NULL COMMENT '发货时间',
    `receive_time` datetime DEFAULT NULL COMMENT '收货时间',
    `refund_status` int DEFAULT NULL COMMENT '退款状态',
    `after_sale_status` int DEFAULT NULL COMMENT '售后状态',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_trade_order_sub_order_id` (`order_id`),
    KEY `idx_trade_order_sub_merchant_id` (`merchant_id`),
    UNIQUE KEY `uk_trade_order_sub_order_merchant` (`order_id`, `merchant_id`)
) COMMENT='商家履约订单';

CREATE TABLE IF NOT EXISTS `product_merchant_data_quality_issue` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `issue_key` varchar(191) NOT NULL,
    `issue_type` varchar(64) NOT NULL,
    `table_name` varchar(64) NOT NULL,
    `record_id` bigint DEFAULT NULL,
    `detail` varchar(1000) NOT NULL,
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待处理 1已修复 2已忽略',
    `first_seen` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_seen` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_merchant_data_quality_issue_key` (`issue_key`),
    KEY `idx_product_merchant_data_quality_issue_status` (`status`, `issue_type`)
) COMMENT='多商家数据治理问题台账';

-- 先处理历史重复子单，再建立唯一约束，避免脏数据阻断升级。
DROP TEMPORARY TABLE IF EXISTS `tmp_trade_order_sub_duplicate`;
CREATE TEMPORARY TABLE `tmp_trade_order_sub_duplicate` (
    `order_id` bigint NOT NULL,
    `merchant_id` bigint NOT NULL,
    `keep_id` bigint NOT NULL,
    PRIMARY KEY (`order_id`, `merchant_id`)
);
INSERT INTO `tmp_trade_order_sub_duplicate` (`order_id`, `merchant_id`, `keep_id`)
SELECT `order_id`, `merchant_id`, MIN(`id`)
FROM `trade_order_sub`
GROUP BY `order_id`, `merchant_id`
HAVING COUNT(*) > 1;
INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('trade_order_sub:duplicate:', `order_id`, ':', `merchant_id`),
       'DUPLICATE_SUB_ORDER', 'trade_order_sub', `keep_id`,
       CONCAT('订单 ', `order_id`, ' 商家 ', `merchant_id`, ' 存在重复子单，保留最早记录'), 0
FROM `tmp_trade_order_sub_duplicate`;
UPDATE `trade_order_item` i
JOIN `trade_order_sub` s ON s.`id` = i.`sub_order_id`
JOIN `tmp_trade_order_sub_duplicate` d ON d.`order_id` = s.`order_id`
                                      AND d.`merchant_id` = s.`merchant_id`
SET i.`sub_order_id` = d.`keep_id`
WHERE s.`id` <> d.`keep_id`;
DELETE s FROM `trade_order_sub` s
JOIN `tmp_trade_order_sub_duplicate` d ON d.`order_id` = s.`order_id`
                                      AND d.`merchant_id` = s.`merchant_id`
WHERE s.`id` <> d.`keep_id`;
DROP TEMPORARY TABLE `tmp_trade_order_sub_duplicate`;

SET @add_trade_sub_unique_index = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `trade_order_sub` ADD UNIQUE KEY `uk_trade_order_sub_order_merchant` (`order_id`, `merchant_id`)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'trade_order_sub'
      AND index_name = 'uk_trade_order_sub_order_merchant'
);
PREPARE add_trade_sub_unique_index_stmt FROM @add_trade_sub_unique_index;
EXECUTE add_trade_sub_unique_index_stmt;
DEALLOCATE PREPARE add_trade_sub_unique_index_stmt;

-- 现有数据迁移：平台自营商家承接历史品牌和商品。
INSERT INTO `product_merchant` (`name`, `store_name`, `status`)
SELECT '平台自营商家', '平台自营', 20
WHERE NOT EXISTS (SELECT 1 FROM `product_merchant` WHERE `name` = '平台自营商家' AND `deleted` = b'0');
UPDATE `product_brand` b SET b.`merchant_id` = (SELECT MIN(m.`id`) FROM `product_merchant` m WHERE m.`name` = '平台自营商家') WHERE b.`merchant_id` IS NULL;
UPDATE `product_spu` s SET s.`merchant_id` = (SELECT MIN(m.`id`) FROM `product_merchant` m WHERE m.`name` = '平台自营商家') WHERE s.`merchant_id` IS NULL;

-- 历史订单项补齐商家快照。商品已删除或商家缺失时，统一归入平台自营商家。
UPDATE `trade_order_item` i
LEFT JOIN `product_spu` s ON s.`id` = i.`spu_id`
LEFT JOIN `product_merchant` sm ON sm.`id` = s.`merchant_id` AND sm.`deleted` = b'0'
SET i.`merchant_id` = COALESCE(s.`merchant_id`, (SELECT MIN(m.`id`) FROM `product_merchant` m WHERE m.`name` = '平台自营商家')),
    i.`merchant_name` = COALESCE(sm.`store_name`, sm.`name`, '平台自营商家')
WHERE i.`merchant_id` IS NULL;

-- 为历史订单生成商家履约订单，使旧订单也能在统一订单详情中按商家展示。
INSERT INTO `trade_order_sub` (`order_id`, `no`, `user_id`, `merchant_id`, `merchant_name`, `status`,
                               `product_count`, `total_price`, `discount_price`, `coupon_price`, `point_price`,
                               `vip_price`, `adjust_price`, `delivery_price`, `pay_price`, `delivery_type`,
                               `logistics_id`, `logistics_no`, `delivery_time`, `receive_time`,
                               `refund_status`, `after_sale_status`, `tenant_id`)
SELECT i.`order_id`, CONCAT('M', LPAD(CAST(i.`merchant_id` AS CHAR), 19, '0'),
       LPAD(CAST(i.`order_id` AS CHAR), 12, '0')), o.`user_id`, i.`merchant_id`,
       MAX(i.`merchant_name`), o.`status`, SUM(i.`count`),
       SUM(i.`price` * i.`count`), SUM(COALESCE(i.`discount_price`, 0)), SUM(COALESCE(i.`coupon_price`, 0)),
       SUM(COALESCE(i.`point_price`, 0)), SUM(COALESCE(i.`vip_price`, 0)), SUM(COALESCE(i.`adjust_price`, 0)),
       SUM(COALESCE(i.`delivery_price`, 0)), SUM(COALESCE(i.`pay_price`, 0)), o.`delivery_type`,
       o.`logistics_id`, o.`logistics_no`, o.`delivery_time`, o.`receive_time`,
       COALESCE(o.`refund_status`, 0), MAX(COALESCE(i.`after_sale_status`, 0)), o.`tenant_id`
FROM `trade_order_item` i
JOIN `trade_order` o ON o.`id` = i.`order_id`
LEFT JOIN `trade_order_sub` existing ON existing.`order_id` = i.`order_id`
                                    AND existing.`merchant_id` = i.`merchant_id`
WHERE i.`merchant_id` IS NOT NULL
  AND existing.`id` IS NULL
GROUP BY i.`order_id`, i.`merchant_id`, o.`no`, o.`user_id`, o.`status`, o.`delivery_type`,
         o.`logistics_id`, o.`logistics_no`, o.`delivery_time`, o.`receive_time`, o.`refund_status`, o.`tenant_id`;

UPDATE `trade_order_item` i
JOIN `trade_order_sub` s ON s.`order_id` = i.`order_id` AND s.`merchant_id` = i.`merchant_id`
SET i.`sub_order_id` = s.`id`
WHERE i.`sub_order_id` IS NULL;

-- 多商家商城管理菜单（超级管理员可直接使用，其他角色可在权限管理中授权）
-- 移除旧版允许平台直接创建商家的权限，商家必须通过自助申请和审核流程。
DELETE FROM `system_menu` WHERE `permission` = 'product:merchant:create';
DELETE FROM `system_menu` WHERE `permission` IN ('product:merchant-qualification:create',
                                                 'product:merchant-qualification:update',
                                                 'product:merchant-qualification:delete',
                                                 'product:merchant-qualification:review');
INSERT IGNORE INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
(12740,'商家管理','',2,2,209,'merchant','ep:shop','mall/product/merchant/index','ProductMerchant',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(12741,'商家查询','product:merchant:query',3,1,12740,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(12743,'商家更新','product:merchant:update',3,2,12740,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(12744,'商家审核','product:merchant:review',3,3,12740,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
(12745,'资质查询','product:merchant-qualification:query',3,4,12740,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');

-- 系统管理：商家工作台账号管理菜单及权限按钮。
-- IDs 12749-12752 follow the merchant menu seed range above; path/permission checks keep reruns idempotent.
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 12749,'商家账号管理','',2,7,1,'merchant-user','ep:user','system/merchant-user/index','SystemMerchantUser',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 12749 OR (`type` = 2 AND `parent_id` = 1 AND `path` = 'merchant-user')
);
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 12750,'商家账号查询','system:merchant-user:query',3,1,12749,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 12750 OR `permission` = 'system:merchant-user:query'
);
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 12751,'商家账号更新','system:merchant-user:update',3,2,12749,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 12751 OR `permission` = 'system:merchant-user:update'
);
INSERT INTO `system_menu` (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 12752,'商家账号重置密码','system:merchant-user:update-password',3,3,12749,'','','','',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `id` = 12752 OR `permission` = 'system:merchant-user:update-password'
);

-- Merchant self-service applications and review history. Existing merchant rows remain unbound.
ALTER TABLE `product_merchant`
    MODIFY COLUMN `credit_code` varchar(2048) DEFAULT NULL COMMENT '统一社会信用代码（加密）',
    MODIFY COLUMN `legal_person` varchar(2048) DEFAULT NULL COMMENT '法人（加密）',
    MODIFY COLUMN `contact_name` varchar(2048) DEFAULT NULL COMMENT '联系人（加密）',
    MODIFY COLUMN `contact_mobile` varchar(2048) DEFAULT NULL COMMENT '联系手机（加密）';
CALL `tmp_add_column_if_missing`('product_merchant', 'owner_account_id', 'bigint DEFAULT NULL COMMENT ''商家账号编号''');
CALL `tmp_add_column_if_missing`('product_merchant', 'business_type', 'varchar(64) DEFAULT NULL COMMENT ''经营类型''');
CALL `tmp_add_column_if_missing`('product_merchant', 'business_types', 'text COMMENT ''经营类型快照(JSON)''');
CALL `tmp_add_column_if_missing`('product_merchant', 'approved_revision', 'int DEFAULT NULL COMMENT ''当前生效申请版本号''');
CALL `tmp_add_column_if_missing`('product_merchant', 'description', 'varchar(512) DEFAULT NULL COMMENT ''店铺简介''');
CALL `tmp_add_column_if_missing`('product_merchant_qualification', 'file_id', 'bigint DEFAULT NULL COMMENT ''私有资质文件编号''');
SET @add_merchant_owner_idx = (
    SELECT IF(COUNT(*) = 0,
              'CREATE UNIQUE INDEX `uk_product_merchant_owner_account` ON `product_merchant` (`owner_account_id`)',
              'SELECT 1')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'product_merchant'
      AND index_name = 'uk_product_merchant_owner_account'
);
PREPARE add_merchant_owner_idx_stmt FROM @add_merchant_owner_idx;
EXECUTE add_merchant_owner_idx_stmt;
DEALLOCATE PREPARE add_merchant_owner_idx_stmt;

CREATE TABLE IF NOT EXISTS `product_merchant_business_type` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(64) NOT NULL,
    `name` varchar(128) NOT NULL,
    `enabled` bit NOT NULL DEFAULT b'1',
    `sort` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) COMMENT='商家经营类型配置';

CREATE TABLE IF NOT EXISTS `product_merchant_qualification_requirement` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `business_type` varchar(64) NOT NULL,
    `code` varchar(64) NOT NULL,
    `name` varchar(128) NOT NULL,
    `required` bit NOT NULL DEFAULT b'1',
    `certificate_no_required` bit NOT NULL DEFAULT b'0',
    `certificate_no_pattern` varchar(2048) DEFAULT NULL,
    `validity_required` bit NOT NULL DEFAULT b'0',
    `enabled` bit NOT NULL DEFAULT b'1',
    `sort` int NOT NULL DEFAULT 0,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) COMMENT='商家资质要求配置';

CREATE TABLE IF NOT EXISTS `product_merchant_application` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `merchant_id` bigint NOT NULL,
    `owner_account_id` bigint DEFAULT NULL,
    `version_no` int NOT NULL,
    `type` varchar(32) NOT NULL COMMENT 'INITIAL/NEW/CHANGE/LEGACY',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '0草稿 10待审 20通过 30驳回',
    `business_type` varchar(64) DEFAULT NULL,
    `business_types` text COMMENT '经营类型快照(JSON)',
    `merchant_name` varchar(128) DEFAULT NULL,
    `store_name` varchar(128) DEFAULT NULL,
    `logo_url` varchar(512) DEFAULT NULL,
    `company_name` varchar(255) DEFAULT NULL,
    `credit_code` varchar(2048) DEFAULT NULL COMMENT '统一社会信用代码（加密）',
    `legal_person` varchar(2048) DEFAULT NULL COMMENT '法人（加密）',
    `contact_name` varchar(2048) DEFAULT NULL COMMENT '联系人（加密）',
    `contact_mobile` varchar(2048) DEFAULT NULL COMMENT '联系手机（加密）',
    `submitted_time` datetime DEFAULT NULL,
    `agreement_version` varchar(64) DEFAULT NULL,
    `privacy_policy_version` varchar(64) DEFAULT NULL,
    `consent_accepted` bit DEFAULT NULL,
    `consent_time` datetime DEFAULT NULL,
    `consent_ip` varchar(64) DEFAULT NULL,
    `reviewer_id` bigint DEFAULT NULL,
    `review_time` datetime DEFAULT NULL,
    `reject_reason` varchar(512) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_product_merchant_application_version` (`merchant_id`, `version_no`),
    KEY `idx_product_merchant_application_status` (`status`, `submitted_time`),
    KEY `idx_product_merchant_application_owner` (`owner_account_id`)
) COMMENT='商家申请版本';

CREATE TABLE IF NOT EXISTS `product_merchant_application_qualification` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `application_id` bigint NOT NULL,
    `code` varchar(64) NOT NULL,
    `name` varchar(128) DEFAULT NULL,
    `required` bit DEFAULT NULL,
    `certificate_no_required` bit NOT NULL DEFAULT b'0',
    `certificate_no_pattern` varchar(2048) DEFAULT NULL,
    `validity_required` bit NOT NULL DEFAULT b'0',
    `certificate_no` varchar(2048) DEFAULT NULL COMMENT '证件编号（加密）',
    `file_id` bigint DEFAULT NULL COMMENT '私有资质文件编号',
    `legacy_file_url` text DEFAULT NULL COMMENT '历史证件地址密文，仅审计留存，不对外返回',
    `valid_start` date DEFAULT NULL,
    `valid_end` date DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) COMMENT='商家申请资质快照';

CREATE TABLE IF NOT EXISTS `product_merchant_review_record` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `merchant_id` bigint NOT NULL,
    `application_id` bigint DEFAULT NULL,
    `version_no` int DEFAULT NULL,
    `action` varchar(16) NOT NULL,
    `reason` varchar(512) DEFAULT NULL,
    `operator_id` bigint DEFAULT NULL COMMENT '操作人编号',
    `operator_type` tinyint DEFAULT NULL COMMENT '操作人类型',
    `status_from` tinyint DEFAULT NULL COMMENT '操作前状态',
    `status_to` tinyint DEFAULT NULL COMMENT '操作后状态',
    `reviewer_id` bigint DEFAULT NULL,
    `review_time` datetime NOT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`), KEY `idx_product_merchant_review_record_merchant` (`merchant_id`, `review_time`)
) COMMENT='商家申请审核记录';

ALTER TABLE `product_merchant_application`
    MODIFY COLUMN `owner_account_id` bigint DEFAULT NULL,
    MODIFY COLUMN `credit_code` varchar(2048) DEFAULT NULL COMMENT '统一社会信用代码（加密）',
    MODIFY COLUMN `legal_person` varchar(2048) DEFAULT NULL COMMENT '法人（加密）',
    MODIFY COLUMN `contact_name` varchar(2048) DEFAULT NULL COMMENT '联系人（加密）',
    MODIFY COLUMN `contact_mobile` varchar(2048) DEFAULT NULL COMMENT '联系手机（加密）';
ALTER TABLE `product_merchant_application_qualification`
    MODIFY COLUMN `certificate_no` varchar(2048) DEFAULT NULL COMMENT '证件编号（加密）';
CALL `tmp_add_column_if_missing`('product_merchant_application', 'business_types', 'text COMMENT ''经营类型快照(JSON)''');
CALL `tmp_add_column_if_missing`('product_merchant_application', 'agreement_version', 'varchar(64) DEFAULT NULL COMMENT ''商家协议版本''');
CALL `tmp_add_column_if_missing`('product_merchant_application', 'privacy_policy_version', 'varchar(64) DEFAULT NULL COMMENT ''隐私政策版本''');
CALL `tmp_add_column_if_missing`('product_merchant_application', 'consent_accepted', 'bit DEFAULT NULL COMMENT ''提交时是否同意''');
CALL `tmp_add_column_if_missing`('product_merchant_application', 'consent_time', 'datetime DEFAULT NULL COMMENT ''同意时间''');
CALL `tmp_add_column_if_missing`('product_merchant_application', 'consent_ip', 'varchar(64) DEFAULT NULL COMMENT ''提交请求IP''');
CALL `tmp_add_column_if_missing`('product_merchant_qualification_requirement', 'certificate_no_required', 'bit NOT NULL DEFAULT b''0'' COMMENT ''是否要求证件编号''');
CALL `tmp_add_column_if_missing`('product_merchant_qualification_requirement', 'validity_required', 'bit NOT NULL DEFAULT b''0'' COMMENT ''是否要求有效期''');
CALL `tmp_add_column_if_missing`('product_merchant_qualification_requirement', 'certificate_no_pattern', 'varchar(2048) DEFAULT NULL COMMENT ''证件编号格式规则快照''');
CALL `tmp_add_column_if_missing`('product_merchant_application_qualification', 'certificate_no_required', 'bit NOT NULL DEFAULT b''0'' COMMENT ''快照：是否要求证件编号''');
CALL `tmp_add_column_if_missing`('product_merchant_application_qualification', 'validity_required', 'bit NOT NULL DEFAULT b''0'' COMMENT ''快照：是否要求有效期''');
CALL `tmp_add_column_if_missing`('product_merchant_application_qualification', 'certificate_no_pattern', 'varchar(2048) DEFAULT NULL COMMENT ''证件编号格式规则快照''');
CALL `tmp_add_column_if_missing`('product_merchant_application_qualification', 'legacy_file_url', 'text DEFAULT NULL COMMENT ''历史证件地址密文，仅审计留存，不对外返回''');
ALTER TABLE `product_merchant_review_record`
    MODIFY COLUMN `reviewer_id` bigint DEFAULT NULL,
    MODIFY COLUMN `application_id` bigint DEFAULT NULL,
    MODIFY COLUMN `version_no` int DEFAULT NULL;
CALL `tmp_add_column_if_missing`('product_merchant_review_record', 'operator_id', 'bigint DEFAULT NULL COMMENT ''操作人编号''');
CALL `tmp_add_column_if_missing`('product_merchant_review_record', 'operator_type', 'tinyint DEFAULT NULL COMMENT ''操作人类型''');
CALL `tmp_add_column_if_missing`('product_merchant_review_record', 'status_from', 'tinyint DEFAULT NULL COMMENT ''操作前状态''');
CALL `tmp_add_column_if_missing`('product_merchant_review_record', 'status_to', 'tinyint DEFAULT NULL COMMENT ''操作后状态''');
DROP PROCEDURE `tmp_add_column_if_missing`;

-- 历史主体全部生成导入快照，保留原审核状态，不将未审核主体升级为已通过。
-- owner 和协议同意保留为空；后续仅可关联已注册且未绑定的账号，由其补正并重新提交。
INSERT INTO `product_merchant_application` (
    `merchant_id`, `owner_account_id`, `version_no`, `type`, `status`, `business_type`, `business_types`,
    `merchant_name`, `store_name`, `logo_url`, `company_name`, `credit_code`, `legal_person`,
    `contact_name`, `contact_mobile`, `submitted_time`, `reviewer_id`, `review_time`, `reject_reason`,
    `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT m.`id`, NULL, 1, 'LEGACY', CASE WHEN m.`status` IN (20, 40) THEN 20 ELSE m.`status` END,
       m.`business_type`, m.`business_types`,
       m.`name`, m.`store_name`, m.`logo_url`, m.`company_name`, m.`credit_code`, m.`legal_person`,
       m.`contact_name`, m.`contact_mobile`, CASE WHEN m.`status` = 0 THEN NULL
            ELSE COALESCE(m.`review_time`, m.`create_time`, NOW()) END,
       m.`reviewer_id`, m.`review_time`, m.`reject_reason`, COALESCE(m.`creator`, ''), COALESCE(m.`create_time`, NOW()),
       COALESCE(m.`updater`, ''), COALESCE(m.`update_time`, NOW()), b'0', m.`tenant_id`
FROM `product_merchant` m
WHERE m.`deleted` = b'0' AND m.`owner_account_id` IS NULL AND m.`status` IN (0, 10, 20, 30, 40)
  AND NOT EXISTS (SELECT 1 FROM `product_merchant_application` a WHERE a.`merchant_id` = m.`id`);

UPDATE `product_merchant` m
JOIN `product_merchant_application` a ON a.`merchant_id` = m.`id` AND a.`type` = 'LEGACY'
SET m.`approved_revision` = a.`version_no`
WHERE m.`deleted` = b'0' AND m.`status` IN (20, 40) AND m.`approved_revision` IS NULL;

-- 仅将唯一匹配的私有 S3 文件转为文件编号，不解密证件地址、不改变存储可见性。
-- 分库部署或尚未安装私有存储字段时跳过，保留密文并在补正时重新上传。
DROP PROCEDURE IF EXISTS `tmp_match_legacy_private_qualification_files`;
DELIMITER $$
CREATE PROCEDURE `tmp_match_legacy_private_qualification_files`()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name IN ('infra_file', 'infra_file_config')) = 2
       AND EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'infra_file_config' AND column_name = 'private') THEN
        UPDATE `product_merchant_qualification` q
        JOIN (
            SELECT q2.`id` AS qualification_id, MIN(f.`id`) AS file_id
            FROM `product_merchant_qualification` q2
            JOIN `infra_file` f ON BINARY f.`url` = BINARY CASE
                WHEN q2.`file_url` LIKE 'PLAIN:v0:%' THEN SUBSTRING(q2.`file_url`, 10)
                ELSE q2.`file_url` END AND f.`deleted` = b'0'
            JOIN `infra_file_config` fc ON fc.`id` = f.`config_id`
                AND fc.`private` = b'1' AND fc.`storage` = 20 AND fc.`deleted` = b'0'
            WHERE q2.`file_id` IS NULL AND q2.`deleted` = b'0'
            GROUP BY q2.`id` HAVING COUNT(*) = 1
        ) matched ON matched.qualification_id = q.`id`
        SET q.`file_id` = matched.file_id
        WHERE q.`file_id` IS NULL;
    END IF;
END$$
DELIMITER ;
CALL `tmp_match_legacy_private_qualification_files`();
DROP PROCEDURE `tmp_match_legacy_private_qualification_files`;

INSERT INTO `product_merchant_application_qualification` (
    `application_id`, `code`, `name`, `required`, `certificate_no_required`, `validity_required`,
    `certificate_no`, `file_id`, `legacy_file_url`, `valid_start`, `valid_end`, `creator`, `create_time`, `updater`,
    `update_time`, `deleted`, `tenant_id`)
SELECT a.`id`, q.`type`, q.`type`, NULL, b'0', b'0', q.`certificate_no`, q.`file_id`, q.`file_url`,
       q.`valid_start`, q.`valid_end`,
       COALESCE(q.`creator`, ''), COALESCE(q.`create_time`, NOW()), COALESCE(q.`updater`, ''),
       COALESCE(q.`update_time`, NOW()), b'0', q.`tenant_id`
FROM `product_merchant` m
JOIN `product_merchant_application` a ON a.`merchant_id` = m.`id` AND a.`type` = 'LEGACY' AND a.`version_no` = 1
JOIN `product_merchant_qualification` q ON q.`merchant_id` = m.`id` AND q.`deleted` = b'0'
    AND q.`id` = (SELECT MIN(q2.`id`) FROM `product_merchant_qualification` q2
                  WHERE q2.`merchant_id` = m.`id` AND q2.`type` = q.`type` AND q2.`deleted` = b'0')
WHERE m.`deleted` = b'0' AND m.`owner_account_id` IS NULL AND m.`status` IN (0, 10, 20, 30, 40)
  AND NOT EXISTS (
      SELECT 1 FROM `product_merchant_application_qualification` aq
      WHERE aq.`application_id` = a.`id` AND aq.`code` = q.`type`);

-- 幂等补齐旧迁移遗漏的证件密文/私有引用，仅适用于无账号的 LEGACY 导入快照。
UPDATE `product_merchant_application_qualification` aq
JOIN `product_merchant_application` a ON a.`id` = aq.`application_id`
    AND a.`type` = 'LEGACY' AND a.`owner_account_id` IS NULL
JOIN `product_merchant_qualification` q ON q.`merchant_id` = a.`merchant_id`
    AND q.`type` = aq.`code` AND q.`deleted` = b'0'
    AND q.`id` = (SELECT MIN(q2.`id`) FROM `product_merchant_qualification` q2
                  WHERE q2.`merchant_id` = q.`merchant_id` AND q2.`type` = q.`type` AND q2.`deleted` = b'0')
SET aq.`legacy_file_url` = COALESCE(aq.`legacy_file_url`, q.`file_url`),
    aq.`file_id` = COALESCE(aq.`file_id`, q.`file_id`)
WHERE aq.`deleted` = b'0' AND (aq.`legacy_file_url` IS NULL OR aq.`file_id` IS NULL);

INSERT INTO `product_merchant_review_record` (
    `merchant_id`, `application_id`, `version_no`, `action`, `reason`, `operator_id`, `operator_type`,
    `status_from`, `status_to`, `reviewer_id`, `review_time`, `creator`, `create_time`, `updater`,
    `update_time`, `deleted`, `tenant_id`)
SELECT m.`id`, a.`id`, a.`version_no`, 'LEGACY', '历史主体导入，保留原审核状态',
       NULL, NULL, m.`status`, m.`status`,
       NULL, COALESCE(m.`review_time`, m.`create_time`, NOW()), COALESCE(m.`creator`, ''),
       COALESCE(m.`create_time`, NOW()), COALESCE(m.`updater`, ''), COALESCE(m.`update_time`, NOW()),
       b'0', m.`tenant_id`
FROM `product_merchant` m
JOIN `product_merchant_application` a ON a.`merchant_id` = m.`id` AND a.`type` = 'LEGACY' AND a.`version_no` = 1
WHERE m.`deleted` = b'0' AND m.`owner_account_id` IS NULL AND m.`status` IN (0, 10, 20, 30, 40)
  AND NOT EXISTS (
      SELECT 1 FROM `product_merchant_review_record` r
      WHERE r.`application_id` = a.`id` AND r.`action` = 'LEGACY');

-- 数据治理：先记录异常，再修复可确定的商品、品牌、订单归属；脚本可重复执行。
SET @platform_merchant_id := (
    SELECT MIN(`id`) FROM `product_merchant`
    WHERE `name` = '平台自营商家' AND `deleted` = b'0'
);

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('product_spu:merchant:', s.`id`), 'PRODUCT_WITHOUT_MERCHANT', 'product_spu', s.`id`,
       '商品缺少商家、商家不存在/已删除或商家租户不一致', s.`tenant_id`
FROM `product_spu` s
LEFT JOIN `product_merchant` m ON m.`id` = s.`merchant_id` AND m.`deleted` = b'0'
    AND (m.`tenant_id` = 0 OR m.`tenant_id` = s.`tenant_id`)
WHERE s.`merchant_id` IS NULL OR m.`id` IS NULL;

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('product_brand:merchant:', b.`id`), 'BRAND_WITHOUT_MERCHANT', 'product_brand', b.`id`,
       '品牌缺少商家、商家不存在/已删除或商家租户不一致', b.`tenant_id`
FROM `product_brand` b
LEFT JOIN `product_merchant` m ON m.`id` = b.`merchant_id` AND m.`deleted` = b'0'
    AND (m.`tenant_id` = 0 OR m.`tenant_id` = b.`tenant_id`)
WHERE b.`merchant_id` IS NULL OR m.`id` IS NULL;

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('trade_order_item:merchant:', i.`id`), 'ORDER_ITEM_MERCHANT_SNAPSHOT', 'trade_order_item', i.`id`,
       '订单项缺少有效商家或商家名称快照', i.`tenant_id`
FROM `trade_order_item` i
LEFT JOIN `product_spu` s ON s.`id` = i.`spu_id`
LEFT JOIN `product_merchant` m ON m.`id` = s.`merchant_id` AND m.`deleted` = b'0'
    AND (m.`tenant_id` = 0 OR m.`tenant_id` = i.`tenant_id`)
LEFT JOIN `product_merchant` im ON im.`id` = i.`merchant_id` AND im.`deleted` = b'0'
    AND (im.`tenant_id` = 0 OR im.`tenant_id` = i.`tenant_id`)
WHERE i.`merchant_id` IS NULL OR i.`merchant_name` IS NULL OR m.`id` IS NULL OR im.`id` IS NULL;

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('trade_order_item:sub_order:', i.`id`), 'MISSING_SUB_ORDER', 'trade_order_item', i.`id`,
       '订单项缺少商家子订单关联', i.`tenant_id`
FROM `trade_order_item` i
LEFT JOIN `trade_order_sub` s ON s.`id` = i.`sub_order_id`
WHERE i.`sub_order_id` IS NULL OR s.`id` IS NULL;

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('trade_order_sub:amount:', s.`id`), 'SUB_ORDER_AMOUNT_MISMATCH', 'trade_order_sub', s.`id`,
       '商家子订单数量或金额与订单项汇总不一致', s.`tenant_id`
FROM `trade_order_sub` s
JOIN (
    SELECT `sub_order_id`, COUNT(*) AS `item_count`, SUM(`count`) AS `product_count`,
           SUM(`price` * `count`) AS `total_price`, SUM(COALESCE(`pay_price`, 0)) AS `pay_price`
    FROM `trade_order_item`
    WHERE `sub_order_id` IS NOT NULL
    GROUP BY `sub_order_id`
) a ON a.`sub_order_id` = s.`id`
WHERE s.`product_count` <> a.`product_count`
   OR s.`total_price` <> a.`total_price`
   OR s.`pay_price` <> a.`pay_price`;

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('promotion_combination_product:', p.`id`), 'PROMOTION_INVALID_PRODUCT',
       'promotion_combination_product', p.`id`, '促销商品引用不存在、删除或归属不一致的 SPU/SKU', p.`tenant_id`
FROM `promotion_combination_product` p
LEFT JOIN `product_spu` s ON s.`id` = p.`spu_id` AND s.`deleted` = b'0'
LEFT JOIN `product_sku` k ON k.`id` = p.`sku_id` AND k.`deleted` = b'0'
WHERE p.`deleted` = b'0' AND (s.`id` IS NULL OR k.`id` IS NULL OR k.`spu_id` <> p.`spu_id`);

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('promotion_discount_product:', p.`id`), 'PROMOTION_INVALID_PRODUCT',
       'promotion_discount_product', p.`id`, '促销商品引用不存在、删除或归属不一致的 SPU/SKU', p.`tenant_id`
FROM `promotion_discount_product` p
LEFT JOIN `product_spu` s ON s.`id` = p.`spu_id` AND s.`deleted` = b'0'
LEFT JOIN `product_sku` k ON k.`id` = p.`sku_id` AND k.`deleted` = b'0'
WHERE p.`deleted` = b'0' AND (s.`id` IS NULL OR k.`id` IS NULL OR k.`spu_id` <> p.`spu_id`);

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('promotion_point_product:', p.`id`), 'PROMOTION_INVALID_PRODUCT',
       'promotion_point_product', p.`id`, '促销商品引用不存在、删除或归属不一致的 SPU/SKU', p.`tenant_id`
FROM `promotion_point_product` p
LEFT JOIN `product_spu` s ON s.`id` = p.`spu_id` AND s.`deleted` = b'0'
LEFT JOIN `product_sku` k ON k.`id` = p.`sku_id` AND k.`deleted` = b'0'
WHERE p.`deleted` = b'0' AND (s.`id` IS NULL OR k.`id` IS NULL OR k.`spu_id` <> p.`spu_id`);

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('promotion_seckill_product:', p.`id`), 'PROMOTION_INVALID_PRODUCT',
       'promotion_seckill_product', p.`id`, '促销商品引用不存在、删除或归属不一致的 SPU/SKU', p.`tenant_id`
FROM `promotion_seckill_product` p
LEFT JOIN `product_spu` s ON s.`id` = p.`spu_id` AND s.`deleted` = b'0'
LEFT JOIN `product_sku` k ON k.`id` = p.`sku_id` AND k.`deleted` = b'0'
WHERE p.`deleted` = b'0' AND (s.`id` IS NULL OR k.`id` IS NULL OR k.`spu_id` <> p.`spu_id`);

INSERT IGNORE INTO `product_merchant_data_quality_issue`
    (`issue_key`, `issue_type`, `table_name`, `record_id`, `detail`, `tenant_id`)
SELECT CONCAT('system_merchant_user:binding:', u.`id`), 'MERCHANT_ACCOUNT_BINDING',
       'system_merchant_user', u.`id`, '商家账号绑定了不存在/删除商家或与商家主体账号不一致', u.`tenant_id`
FROM `system_merchant_user` u
LEFT JOIN `product_merchant` m ON m.`id` = u.`merchant_id` AND m.`deleted` = b'0'
WHERE u.`merchant_id` IS NOT NULL AND (m.`id` IS NULL OR m.`owner_account_id` IS NULL
    OR m.`owner_account_id` <> u.`id`);

-- 可确定的历史归属统一修复为平台自营商家；订单快照只在缺失或无效时补齐。
UPDATE `product_spu` s
LEFT JOIN `product_merchant` m ON m.`id` = s.`merchant_id` AND m.`deleted` = b'0'
    AND (m.`tenant_id` = 0 OR m.`tenant_id` = s.`tenant_id`)
SET s.`merchant_id` = @platform_merchant_id
WHERE s.`merchant_id` IS NULL OR m.`id` IS NULL;

UPDATE `product_brand` b
LEFT JOIN `product_merchant` m ON m.`id` = b.`merchant_id` AND m.`deleted` = b'0'
    AND (m.`tenant_id` = 0 OR m.`tenant_id` = b.`tenant_id`)
SET b.`merchant_id` = @platform_merchant_id
WHERE b.`merchant_id` IS NULL OR m.`id` IS NULL;

UPDATE `trade_order_item` i
LEFT JOIN `product_spu` s ON s.`id` = i.`spu_id`
LEFT JOIN `product_merchant` m ON m.`id` = s.`merchant_id` AND m.`deleted` = b'0'
    AND (m.`tenant_id` = 0 OR m.`tenant_id` = i.`tenant_id`)
LEFT JOIN `product_merchant` im ON im.`id` = i.`merchant_id` AND im.`deleted` = b'0'
    AND (im.`tenant_id` = 0 OR im.`tenant_id` = i.`tenant_id`)
SET i.`merchant_id` = COALESCE(im.`id`, m.`id`, @platform_merchant_id),
    i.`merchant_name` = COALESCE(NULLIF(i.`merchant_name`, ''), im.`store_name`, im.`name`,
        m.`store_name`, m.`name`, '平台自营商家')
WHERE i.`merchant_id` IS NULL OR i.`merchant_name` IS NULL OR m.`id` IS NULL OR im.`id` IS NULL;

UPDATE `trade_order_item` i
JOIN `trade_order_sub` s ON s.`order_id` = i.`order_id` AND s.`merchant_id` = i.`merchant_id`
SET i.`sub_order_id` = s.`id`
WHERE i.`sub_order_id` IS NULL;

UPDATE `trade_order_sub` s
JOIN `product_merchant` m ON m.`id` = s.`merchant_id`
SET s.`merchant_name` = COALESCE(NULLIF(s.`merchant_name`, ''), m.`store_name`, m.`name`)
WHERE s.`merchant_name` IS NULL OR s.`merchant_name` = '';

ALTER TABLE `product_spu` MODIFY COLUMN `merchant_id` bigint NOT NULL COMMENT '商品销售商家编号';
ALTER TABLE `product_brand` MODIFY COLUMN `merchant_id` bigint NOT NULL COMMENT '品牌默认归属商家编号';
ALTER TABLE `trade_order_item`
    MODIFY COLUMN `merchant_id` bigint NOT NULL COMMENT '商家编号快照',
    MODIFY COLUMN `merchant_name` varchar(128) NOT NULL DEFAULT '平台自营商家' COMMENT '商家名称快照';

UPDATE `product_merchant_data_quality_issue`
SET `status` = 1, `last_seen` = NOW()
WHERE `status` = 0 AND `issue_type` IN ('PRODUCT_WITHOUT_MERCHANT', 'BRAND_WITHOUT_MERCHANT',
    'ORDER_ITEM_MERCHANT_SNAPSHOT', 'MISSING_SUB_ORDER', 'DUPLICATE_SUB_ORDER');

-- 支付表按可选模块安装；若存在，则只登记缺失快照，不篡改历史资金记录。
DROP PROCEDURE IF EXISTS `tmp_audit_merchant_payment_snapshot`;
DELIMITER $$
CREATE PROCEDURE `tmp_audit_merchant_payment_snapshot`()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'pay_fund_order')
       AND (SELECT COUNT(DISTINCT column_name) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'pay_fund_order'
              AND column_name IN ('id', 'tenant_id', 'trade_order_sub_id', 'divide_plan_data')) = 4 THEN
        SET @merchant_payment_sql = 'INSERT IGNORE INTO product_merchant_data_quality_issue (issue_key, issue_type, table_name, record_id, detail, tenant_id) SELECT CONCAT(\'pay_fund_order:divide_snapshot:\', id), \'PAYMENT_SNAPSHOT\', \'pay_fund_order\', id, \'已拆分订单缺少支付分账计划快照\', tenant_id FROM pay_fund_order WHERE trade_order_sub_id IS NOT NULL AND (divide_plan_data IS NULL OR divide_plan_data = \'\')';
        PREPARE merchant_payment_stmt FROM @merchant_payment_sql;
        EXECUTE merchant_payment_stmt;
        DEALLOCATE PREPARE merchant_payment_stmt;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'pay_fund_detail')
       AND (SELECT COUNT(DISTINCT column_name) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'pay_fund_detail'
              AND column_name IN ('id', 'tenant_id', 'trade_order_sub_id', 'merchant_id')) = 4 THEN
        SET @merchant_payment_sql = 'INSERT IGNORE INTO product_merchant_data_quality_issue (issue_key, issue_type, table_name, record_id, detail, tenant_id) SELECT CONCAT(\'pay_fund_detail:merchant_snapshot:\', id), \'PAYMENT_SNAPSHOT\', \'pay_fund_detail\', id, \'资金明细缺少商家快照\', tenant_id FROM pay_fund_detail WHERE trade_order_sub_id IS NOT NULL AND merchant_id IS NULL';
        PREPARE merchant_payment_stmt FROM @merchant_payment_sql;
        EXECUTE merchant_payment_stmt;
        DEALLOCATE PREPARE merchant_payment_stmt;
    END IF;
END$$
DELIMITER ;
CALL `tmp_audit_merchant_payment_snapshot`();
DROP PROCEDURE `tmp_audit_merchant_payment_snapshot`;
