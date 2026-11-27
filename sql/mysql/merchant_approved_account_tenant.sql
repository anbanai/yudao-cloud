-- 已审核商家账号租户回填。在含 system、product 表的数据库执行。支持重复执行。
-- 已登录的历史账号须重新登录以取得带有效租户的令牌。
UPDATE system_merchant_user u
JOIN product_merchant m ON m.id = u.merchant_id AND m.owner_account_id = u.id
JOIN system_tenant t ON t.id = m.tenant_id
SET u.tenant_id = m.tenant_id
WHERE u.tenant_id = 0 AND u.deleted = b'0'
  AND m.tenant_id > 0 AND m.status = 20 AND m.deleted = b'0'
  AND t.status = 0 AND t.deleted = b'0' AND t.expire_time > CURRENT_TIMESTAMP;
