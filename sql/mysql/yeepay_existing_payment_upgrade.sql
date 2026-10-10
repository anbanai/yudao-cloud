-- MySQL 8 已有支付表升级。先安装 yeepay_fund.sql；此脚本可重复执行。

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_order ADD COLUMN divide_plan_data text NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_order' AND column_name = 'divide_plan_data');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_refund ADD COLUMN fund_allocation_data text NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_refund' AND column_name = 'fund_allocation_data');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_refund ADD COLUMN request_submitted_time datetime NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_refund' AND column_name = 'request_submitted_time');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_fund_detail ADD COLUMN divide_amount bigint NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_fund_detail' AND column_name = 'divide_amount');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_refund_fund_detail ADD COLUMN divide_back_success_time datetime NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_refund_fund_detail' AND column_name = 'divide_back_success_time');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_reconciliation_record ADD COLUMN channel_id bigint NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_reconciliation_record' AND column_name = 'channel_id');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_yeepay_merchant_account ADD COLUMN submission_token varchar(64) NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_yeepay_merchant_account' AND column_name = 'submission_token');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_yeepay_merchant_account ADD COLUMN submission_expire_time datetime NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_yeepay_merchant_account' AND column_name = 'submission_expire_time');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;

ALTER TABLE pay_yeepay_merchant_account MODIFY merchant_no varchar(64) DEFAULT NULL;

UPDATE pay_yeepay_merchant_account SET merchant_no = NULL WHERE merchant_no = '';

ALTER TABLE pay_reconciliation_record MODIFY total_amount bigint NOT NULL DEFAULT 0, MODIFY total_price bigint NOT NULL DEFAULT 0, MODIFY matched_amount bigint NOT NULL DEFAULT 0, MODIFY difference_amount bigint NOT NULL DEFAULT 0;

UPDATE pay_refund SET request_submitted_time = create_time WHERE channel_code = 'yeepay_wx_lite' AND request_submitted_time IS NULL;

SET @yeepay_upgrade_ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE pay_yeepay_event ADD COLUMN encrypted_original mediumtext NULL', 'SELECT 1')
    FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'pay_yeepay_event' AND column_name = 'encrypted_original');
PREPARE yeepay_upgrade_stmt FROM @yeepay_upgrade_ddl;
EXECUTE yeepay_upgrade_stmt;
DEALLOCATE PREPARE yeepay_upgrade_stmt;
