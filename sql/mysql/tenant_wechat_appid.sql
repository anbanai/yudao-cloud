-- 为不忍独享微信小程序绑定商城默认租户。
-- 可重复执行：只有未绑定时才追加 AppID，不会产生重复值。
UPDATE system_tenant target
LEFT JOIN system_tenant existing
       ON existing.deleted = 0
      AND FIND_IN_SET('wx427975d783c9d126', COALESCE(existing.websites, '')) <> 0
SET target.websites = CASE
                          WHEN target.websites IS NULL OR TRIM(target.websites) = ''
                              THEN 'wx427975d783c9d126'
                          ELSE CONCAT(target.websites, ',wx427975d783c9d126')
                      END,
    target.update_time = NOW()
WHERE target.id = 1
  AND target.deleted = 0
  AND FIND_IN_SET('wx427975d783c9d126', COALESCE(target.websites, '')) = 0
  AND existing.id IS NULL;
