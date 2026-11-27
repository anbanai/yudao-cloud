-- 为不忍独享微信小程序绑定商城默认租户。
-- 可重复执行：只有未绑定时才追加 AppID，不会产生重复值。
UPDATE system_tenant target
LEFT JOIN system_tenant existing
       ON existing.deleted = 0
      AND FIND_IN_SET('wxb185cc101a8508ab', COALESCE(existing.websites, '')) <> 0
SET target.websites = CASE
                          WHEN target.websites IS NULL OR TRIM(target.websites) = ''
                              THEN 'wxb185cc101a8508ab'
                          ELSE CONCAT(target.websites, ',wxb185cc101a8508ab')
                      END,
    target.update_time = NOW()
WHERE target.id = 1
  AND target.deleted = 0
  AND FIND_IN_SET('wxb185cc101a8508ab', COALESCE(target.websites, '')) = 0
  AND existing.id IS NULL;
