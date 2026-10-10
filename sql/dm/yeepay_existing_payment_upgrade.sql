-- 已有支付表升级；先执行本数据库的原有支付表安装。
-- PostgreSQL / SQL Server / HighGo 可重复执行；其余数据库按版本执行一次。
ALTER TABLE pay_order ADD divide_plan_data clob NULL;
ALTER TABLE pay_refund ADD fund_allocation_data clob NULL;
ALTER TABLE pay_refund ADD request_submitted_time timestamp(6) NULL;
UPDATE pay_refund SET request_submitted_time = create_time WHERE channel_code = 'yeepay_wx_lite' AND request_submitted_time IS NULL;
