-- 为不忍独享微信小程序绑定商城默认租户。
-- 可重复执行：只有未绑定时才追加 AppID，不会产生重复值。
UPDATE system_tenant
SET websites = CASE
                   WHEN websites IS NULL OR TRIM(websites) = '' THEN 'wx427975d783c9d126'
                   WHEN FIND_IN_SET('wx427975d783c9d126', websites) = 0
                       THEN CONCAT(websites, ',wx427975d783c9d126')
                   ELSE websites
               END,
    update_time = NOW()
WHERE id = 1
  AND deleted = 0
  AND FIND_IN_SET('wx427975d783c9d126', COALESCE(websites, '')) = 0
  AND NOT EXISTS (
      SELECT 1
      FROM system_tenant existing
      WHERE existing.deleted = 0
        AND FIND_IN_SET('wx427975d783c9d126', COALESCE(existing.websites, '')) <> 0
  );
