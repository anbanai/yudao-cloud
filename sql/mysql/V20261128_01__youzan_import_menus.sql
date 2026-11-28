-- Run after member_youzan_import.sql and V20261125_01__product_youzan_import.sql.
-- Keep existing menu IDs so existing role-menu associations still reference them.
SET @youzan_member_parent = (
    SELECT id FROM system_menu
    WHERE parent_id = 0 AND path = '/member' AND type = 1 AND deleted = b'0'
);
SET @youzan_product_parent = (
    SELECT product.id FROM system_menu product
    JOIN system_menu mall ON mall.id = product.parent_id
    WHERE mall.parent_id = 0 AND mall.path = '/mall' AND mall.type = 1 AND mall.deleted = b'0'
      AND product.path = 'product' AND product.type = 1 AND product.deleted = b'0'
);

START TRANSACTION;

UPDATE system_menu
SET parent_id = @youzan_member_parent, path = 'youzan-import', update_time = NOW()
WHERE component = 'member/youzan-import/index' AND type = 2 AND deleted = b'0'
  AND @youzan_member_parent IS NOT NULL
  AND (parent_id <> @youzan_member_parent OR path <> 'youzan-import');

UPDATE system_menu
SET parent_id = @youzan_product_parent, path = 'youzan-import', keep_alive = b'1', update_time = NOW()
WHERE component = 'mall/product/youzan-import/index' AND type = 2 AND deleted = b'0'
  AND @youzan_product_parent IS NOT NULL
  AND (parent_id <> @youzan_product_parent OR path <> 'youzan-import' OR keep_alive <> b'1');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '有赞客户积分导入', '', 2, 99, @youzan_member_parent, 'youzan-import', 'ep:upload',
       'member/youzan-import/index', 'MemberYouzanImport', 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
WHERE @youzan_member_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'member/youzan-import/index' AND deleted = b'0');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '有赞商品导入', 'product:youzan:import', 2, 10, @youzan_product_parent, 'youzan-import', 'ep:upload',
       'mall/product/youzan-import/index', 'ProductYouzanImport', 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
WHERE @youzan_product_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'mall/product/youzan-import/index' AND deleted = b'0');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT actions.name, actions.permission, 3, actions.sort, menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (
    SELECT '查询有赞客户导入批次' AS name, 'member:youzan-import:query' AS permission, 1 AS sort
    UNION ALL SELECT '导入有赞客户积分快照', 'member:youzan-import:import', 2
) actions
JOIN system_menu menu ON menu.component = 'member/youzan-import/index'
    AND menu.parent_id = @youzan_member_parent AND menu.deleted = b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = actions.permission AND deleted = b'0');

-- The login menu tree requires every ancestor to be assigned to the role.
-- Backfill directory links for existing import assignments. Directories with permission codes
-- require an explicit role assignment in the admin UI.
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, updater)
SELECT DISTINCT assignment.role_id, directory.id, assignment.tenant_id, '', ''
FROM system_menu import_page
JOIN system_menu parent ON parent.id = import_page.parent_id AND parent.deleted = b'0'
JOIN system_menu directory ON (directory.id = parent.id OR directory.id = parent.parent_id)
    AND directory.type = 1 AND directory.deleted = b'0' AND directory.permission = ''
JOIN system_role_menu assignment ON assignment.menu_id = import_page.id AND assignment.deleted = b'0'
LEFT JOIN system_role_menu existing ON existing.role_id = assignment.role_id
    AND existing.tenant_id = assignment.tenant_id AND existing.menu_id = directory.id AND existing.deleted = b'0'
WHERE import_page.type = 2 AND import_page.deleted = b'0' AND existing.id IS NULL
  AND ((import_page.component = 'member/youzan-import/index' AND parent.id = @youzan_member_parent)
    OR (import_page.component = 'mall/product/youzan-import/index' AND parent.id = @youzan_product_parent));

COMMIT;
