-- 在 multi_merchant.sql 和易宝支付资金表之后执行（MySQL 8）。可重复执行。
-- 入网资料和签约/认证链接使用 product 的 MerchantSensitiveTypeHandler 加密；不保存易宝密钥。
-- yudao.product.merchant-pay.app-key 默认 mall，与 trade 的支付应用保持一致。
-- 若部署的 app-key 不是 mall，执行下方历史已审批数据回填前修改 SELECT 的应用标识。
-- 调度默认开启，yudao.product.merchant-pay.scheduler-enabled=false 可暂停自动网络调用。

CREATE TABLE IF NOT EXISTS `product_merchant_pay_onboarding` (
    `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `merchant_id` bigint NOT NULL,
    `merchant_approval_time` datetime(6) NOT NULL,
    `app_key` varchar(64) NOT NULL,
    `request_no` varchar(64) NOT NULL,
    `business_role` varchar(32) NOT NULL,
    `state` varchar(32) NOT NULL,
    `materials_json` mediumtext DEFAULT NULL,
    `materials_version` int NOT NULL DEFAULT 0,
    `attempt_count` int NOT NULL DEFAULT 0,
    `request_accepted` bit NOT NULL DEFAULT b'0',
    `next_retry_time` timestamp DEFAULT NULL,
    `last_error` varchar(128) DEFAULT NULL,
    `onboarding_status` int DEFAULT NULL,
    `sign_url` mediumtext DEFAULT NULL,
    `auth_url` mediumtext DEFAULT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    UNIQUE KEY uk_merchant_approval (`tenant_id`, `merchant_id`, `merchant_approval_time`),
    KEY idx_due (`state`, `next_retry_time`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家审批后的支付入网持久化任务';

CREATE TABLE IF NOT EXISTS `product_merchant_pay_material_revision` (
    `id` bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `onboarding_id` bigint NOT NULL,
    `merchant_id` bigint NOT NULL,
    `materials_version` int NOT NULL,
    `business_role` varchar(32) NOT NULL,
    `operator_id` bigint DEFAULT NULL,
    `materials_json` mediumtext NOT NULL,
    `creator` varchar(64) DEFAULT '', `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '', `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
    UNIQUE KEY uk_material_revision (`tenant_id`, `onboarding_id`, `materials_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付入网补料版本审计（加密）';

-- 原有商户表的历史已批准记录进入待补料流程；未引入申请表依赖。
INSERT INTO product_merchant_pay_onboarding
    (merchant_id, merchant_approval_time, app_key, request_no, business_role, state,
     materials_version, attempt_count, request_accepted, tenant_id)
SELECT m.id, COALESCE(m.review_time, m.update_time), 'mall', CONCAT('MER-', m.tenant_id, '-', m.id),
       'SHARE_MERCHANT', 'WAITING_MATERIALS', 0, 0, b'0', m.tenant_id
FROM product_merchant m
WHERE m.status = 20 AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM product_merchant_pay_onboarding p
                  WHERE p.merchant_id = m.id AND p.tenant_id = m.tenant_id
                    AND p.merchant_approval_time = COALESCE(m.review_time, m.update_time));
