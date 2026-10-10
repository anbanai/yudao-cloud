-- 已有支付表升级；先执行本数据库的原有支付表安装。
-- PostgreSQL / SQL Server / HighGo 可重复执行；其余数据库按版本执行一次。
IF COL_LENGTH('pay_order', 'divide_plan_data') IS NULL ALTER TABLE pay_order ADD divide_plan_data nvarchar(max) NULL;
GO
IF COL_LENGTH('pay_refund', 'fund_allocation_data') IS NULL ALTER TABLE pay_refund ADD fund_allocation_data nvarchar(max) NULL;
GO
IF COL_LENGTH('pay_refund', 'request_submitted_time') IS NULL ALTER TABLE pay_refund ADD request_submitted_time datetime2 NULL;
GO
UPDATE pay_refund SET request_submitted_time = create_time WHERE channel_code = 'yeepay_wx_lite' AND request_submitted_time IS NULL;
