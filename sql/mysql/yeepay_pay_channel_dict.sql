-- 易宝微信小程序聚合支付渠道字典（MySQL 8）。
-- 可重复执行；不固定字典数据 id，也不覆盖管理员已有配置。
-- 若支付渠道字典类型尚未初始化，本脚本不会插入孤立字典数据。

START TRANSACTION;

INSERT INTO system_dict_data
    (sort, label, value, dict_type, status, color_type, css_class, remark,
     creator, create_time, updater, update_time, deleted)
SELECT 22, '易宝微信小程序聚合支付', 'yeepay_wx_lite', 'pay_channel_code',
       0, 'primary', '', '易宝平台统一 AppID 的微信小程序聚合支付',
       '1', NOW(), '1', NOW(), b'0'
WHERE EXISTS (
    SELECT 1
    FROM system_dict_type
    WHERE type = 'pay_channel_code' AND deleted = b'0'
)
  AND NOT EXISTS (
    SELECT 1
    FROM system_dict_data
    WHERE dict_type = 'pay_channel_code'
      AND value = 'yeepay_wx_lite'
      AND deleted = b'0'
);

COMMIT;

SELECT COUNT(*) AS yeepay_channel_dict_count
FROM system_dict_data
WHERE dict_type = 'pay_channel_code'
  AND value = 'yeepay_wx_lite'
  AND deleted = b'0';
