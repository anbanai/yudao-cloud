-- 在 yeepay_fund.sql 建立任务表后执行；原有 product_merchant 必须存在。
-- 默认 app_key 为 mall，部署其他应用时先替换；可重复执行，软删除记录不入网。
INSERT INTO product_merchant_pay_onboarding
    (merchant_id, merchant_approval_time, app_key, request_no, business_role, state,
     materials_version, attempt_count, request_accepted, tenant_id)
SELECT m.id, COALESCE(m.review_time, m.update_time), 'mall', CONCAT('MER-', m.tenant_id, '-', m.id),
       'SHARE_MERCHANT', 'WAITING_MATERIALS', 0, 0, 0, m.tenant_id
FROM product_merchant m
WHERE m.status = 20 AND m.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM product_merchant_pay_onboarding p
                  WHERE p.merchant_id = m.id AND p.tenant_id = m.tenant_id
                    AND p.merchant_approval_time = COALESCE(m.review_time, m.update_time));
