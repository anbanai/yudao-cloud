-- 多商家商城一期：商家、商品归属及商家履约订单
CREATE TABLE IF NOT EXISTS `product_merchant` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商家编号',
    `name` varchar(128) NOT NULL COMMENT '商家名称',
    `store_name` varchar(128) DEFAULT NULL COMMENT '店铺名称',
    `logo_url` varchar(512) DEFAULT NULL COMMENT '店铺 logo',
    `company_name` varchar(255) DEFAULT NULL COMMENT '企业主体名称',
    `credit_code` varchar(64) DEFAULT NULL COMMENT '统一社会信用代码',
    `legal_person` varchar(64) DEFAULT NULL COMMENT '法人',
    `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人',
    `contact_mobile` varchar(32) DEFAULT NULL COMMENT '联系手机',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '入驻状态',
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
    `certificate_no` varchar(128) DEFAULT NULL COMMENT '证件编号',
    `file_url` varchar(1024) NOT NULL COMMENT '证件文件',
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

ALTER TABLE `product_brand` ADD COLUMN IF NOT EXISTS `merchant_id` bigint DEFAULT NULL COMMENT '品牌默认归属商家编号';
ALTER TABLE `product_spu` ADD COLUMN IF NOT EXISTS `merchant_id` bigint DEFAULT NULL COMMENT '商品销售商家编号';
ALTER TABLE `trade_order_item` ADD COLUMN IF NOT EXISTS `sub_order_id` bigint DEFAULT NULL COMMENT '商家履约订单编号';
ALTER TABLE `trade_order_item` ADD COLUMN IF NOT EXISTS `merchant_id` bigint DEFAULT NULL COMMENT '商家编号快照';
ALTER TABLE `trade_order_item` ADD COLUMN IF NOT EXISTS `merchant_name` varchar(128) DEFAULT NULL COMMENT '商家名称快照';
ALTER TABLE `trade_logistics_waybill` ADD COLUMN IF NOT EXISTS `sub_order_id` bigint DEFAULT NULL COMMENT '商家履约订单编号' AFTER `order_id`;
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
    SELECT IF(COUNT(*) = 0,
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
    KEY `idx_trade_order_sub_merchant_id` (`merchant_id`)
) COMMENT='商家履约订单';

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
                               `refund_status`, `after_sale_status`)
SELECT i.`order_id`, LEFT(CONCAT(o.`no`, '-', i.`merchant_id`), 32), o.`user_id`, i.`merchant_id`,
       MAX(i.`merchant_name`), o.`status`, SUM(i.`count`),
       SUM(i.`price` * i.`count`), SUM(COALESCE(i.`discount_price`, 0)), SUM(COALESCE(i.`coupon_price`, 0)),
       SUM(COALESCE(i.`point_price`, 0)), SUM(COALESCE(i.`vip_price`, 0)), SUM(COALESCE(i.`adjust_price`, 0)),
       SUM(COALESCE(i.`delivery_price`, 0)), SUM(COALESCE(i.`pay_price`, 0)), o.`delivery_type`,
       COALESCE(o.`refund_status`, 0), 0
FROM `trade_order_item` i
JOIN `trade_order` o ON o.`id` = i.`order_id`
LEFT JOIN `trade_order_sub` existing ON existing.`order_id` = i.`order_id`
                                    AND existing.`merchant_id` = i.`merchant_id`
WHERE i.`merchant_id` IS NOT NULL
  AND existing.`id` IS NULL
GROUP BY i.`order_id`, i.`merchant_id`, o.`no`, o.`user_id`, o.`status`, o.`delivery_type`, o.`refund_status`;

UPDATE `trade_order_item` i
JOIN `trade_order_sub` s ON s.`order_id` = i.`order_id` AND s.`merchant_id` = i.`merchant_id`
SET i.`sub_order_id` = s.`id`
WHERE i.`sub_order_id` IS NULL;
