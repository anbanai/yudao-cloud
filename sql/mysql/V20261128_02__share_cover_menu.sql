-- Execute against the system menu database before deploying the admin frontend.
-- For tenant packages, include the page and the required ancestors in the package menu selection.
START TRANSACTION;

SET @cover_mall = (
    SELECT id FROM system_menu WHERE parent_id = 0 AND path = '/mall' AND type = 1 AND deleted = b'0'
);
SET @cover_promotion = (
    SELECT id FROM system_menu WHERE parent_id = @cover_mall AND path = 'promotion' AND type = 1 AND deleted = b'0'
);

UPDATE system_menu
SET parent_id = @cover_promotion, path = 'share-cover', update_time = NOW()
WHERE component = 'infra/share-cover/index' AND type = 2 AND deleted = b'0'
  AND @cover_promotion IS NOT NULL AND (parent_id <> @cover_promotion OR path <> 'share-cover');

INSERT INTO system_menu
    (name, permission, type, sort, parent_id, path, icon, component, component_name)
SELECT '小程序分享封面', '', 2, 21, @cover_promotion, 'share-cover', 'lucide:image',
       'infra/share-cover/index', 'InfraShareCover'
WHERE @cover_promotion IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'infra/share-cover/index' AND deleted = b'0');

SET @cover_page = (
    SELECT id FROM system_menu WHERE component = 'infra/share-cover/index'
      AND parent_id = @cover_promotion AND type = 2 AND deleted = b'0'
);

UPDATE system_menu
SET parent_id = @cover_page, update_time = NOW()
WHERE permission = 'infra:share-cover:save' AND type = 3 AND deleted = b'0'
  AND @cover_page IS NOT NULL AND parent_id <> @cover_page;

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon)
SELECT '保存分享封面', 'infra:share-cover:save', 3, 1, @cover_page, '', ''
WHERE @cover_page IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM system_menu WHERE permission = 'infra:share-cover:save' AND deleted = b'0');

-- Keep the menu reachable for roles already assigned the page or save permission.
INSERT INTO system_role_menu (role_id, menu_id, tenant_id, creator, updater)
SELECT DISTINCT assignment.role_id, target.id, assignment.tenant_id, '', ''
FROM system_role_menu assignment
JOIN system_menu source ON source.id = assignment.menu_id AND source.deleted = b'0'
JOIN system_menu target ON target.id IN (@cover_page, @cover_promotion, @cover_mall)
    AND target.permission = ''
WHERE assignment.deleted = b'0' AND @cover_page IS NOT NULL
  AND (source.id = @cover_page OR source.permission = 'infra:share-cover:save')
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu existing WHERE existing.role_id = assignment.role_id
        AND existing.tenant_id = assignment.tenant_id AND existing.menu_id = target.id AND existing.deleted = b'0'
  );

COMMIT;

SELECT @cover_page AS share_cover_menu_id,
       IF(@cover_page IS NULL, '缺少商城营销目录，请核对 /mall/promotion 后重跑', '分享封面菜单位于营销目录') AS result;
