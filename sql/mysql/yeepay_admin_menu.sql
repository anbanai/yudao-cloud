-- 易宝资金运营 / 商户支付入网菜单和权限（MySQL 8）。
-- 在现有 system_menu 上执行，可重复执行；不修改现有菜单、不自动赋予任何角色。
-- 执行后在角色管理中按需分配菜单，刷新权限缓存并重新登录。
-- 前端组件仅对应 yudao-ui-admin-vben/apps/web-antd。

START TRANSACTION;
SET @yeepay_pay_menu_id = (
    SELECT id FROM system_menu WHERE path = '/pay' AND type = 1 AND deleted = b'0'
    ORDER BY id LIMIT 1
);

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '资金运营', '', 2, 6, @yeepay_pay_menu_id, 'fund', 'ep:money', 'pay/fund/index', 'PayFund',
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @yeepay_pay_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'pay/fund/index' AND deleted = b'0');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '商户支付入网', '', 2, 7, @yeepay_pay_menu_id, 'fund/merchant', 'ep:shop',
       'pay/fund/merchant', 'PayFundMerchant', 0, b'1', b'0', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @yeepay_pay_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'pay/fund/merchant' AND deleted = b'0');

SET @yeepay_fund_menu_id = (
    SELECT id FROM system_menu WHERE component = 'pay/fund/index' AND deleted = b'0' ORDER BY id LIMIT 1
);
SET @yeepay_merchant_menu_id = (
    SELECT id FROM system_menu WHERE component = 'pay/fund/merchant' AND deleted = b'0' ORDER BY id LIMIT 1
);

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT seed.name, seed.permission, 3, seed.sort, seed.parent_id, '', '', '', '',
       0, b'1', b'0', b'0', '1', NOW(), '1', NOW(), b'0'
FROM (
    SELECT '资金查询' AS name, 'pay:fund:query' AS permission, 1 AS sort, @yeepay_fund_menu_id AS parent_id
    UNION ALL SELECT '分账重试', 'pay:fund:divide:retry', 2, @yeepay_fund_menu_id
    UNION ALL SELECT '退款重试', 'pay:fund:refund:retry', 3, @yeepay_fund_menu_id
    UNION ALL SELECT '对账处理', 'pay:fund:update', 4, @yeepay_fund_menu_id
    UNION ALL SELECT '入网查询', 'pay:fund:merchant:query', 1, @yeepay_merchant_menu_id
    UNION ALL SELECT '入网补料', 'pay:fund:merchant:update', 2, @yeepay_merchant_menu_id
) seed
WHERE seed.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu existing
                  WHERE existing.permission = seed.permission AND existing.deleted = b'0');
COMMIT;

SELECT @yeepay_pay_menu_id AS pay_menu_id, @yeepay_fund_menu_id AS fund_menu_id,
       @yeepay_merchant_menu_id AS merchant_menu_id,
       IF(@yeepay_pay_menu_id IS NULL, '缺少 /pay 支付管理菜单，请先建立父菜单再重跑', '菜单已建立或已存在') AS result;
