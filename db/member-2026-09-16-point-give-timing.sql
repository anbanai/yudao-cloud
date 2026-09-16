-- 订单积分发放时机配置及待生效积分支持
ALTER TABLE `member_config`
    ADD COLUMN IF NOT EXISTS `point_trade_give_timing` tinyint NOT NULL DEFAULT 1 COMMENT '订单赠送积分发放时机：1 支付后，2 收货后';

ALTER TABLE `member_point_record`
    ADD COLUMN IF NOT EXISTS `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1 已生效，2 待生效，3 已作废',
    ADD COLUMN IF NOT EXISTS `effective_time` datetime NULL COMMENT '生效时间';

ALTER TABLE `trade_order`
    ADD COLUMN IF NOT EXISTS `point_give_timing` tinyint NOT NULL DEFAULT 1 COMMENT '赠送积分发放时机：1 支付后，2 收货后';
