-- 在创建 pay_yeepay_merchant_account 后执行；MySQL 8，可重复执行。
-- 稳定 request_no 先提交，再请求易宝；提交租约防止多个实例重复发送。
SET @yeepay_submission_token_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE pay_yeepay_merchant_account ADD COLUMN submission_token varchar(64) DEFAULT NULL COMMENT ''入网提交租约''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'pay_yeepay_merchant_account' AND column_name = 'submission_token'
);
PREPARE yeepay_submission_token_stmt FROM @yeepay_submission_token_ddl;
EXECUTE yeepay_submission_token_stmt;
DEALLOCATE PREPARE yeepay_submission_token_stmt;

SET @yeepay_submission_expiry_ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE pay_yeepay_merchant_account ADD COLUMN submission_expire_time datetime DEFAULT NULL COMMENT ''提交租约到期时间''',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'pay_yeepay_merchant_account' AND column_name = 'submission_expire_time'
);
PREPARE yeepay_submission_expiry_stmt FROM @yeepay_submission_expiry_ddl;
EXECUTE yeepay_submission_expiry_stmt;
DEALLOCATE PREPARE yeepay_submission_expiry_stmt;

-- 渠道未分配商户号时统一使用 NULL，唯一索引允许多个待审核商户共存。
ALTER TABLE pay_yeepay_merchant_account MODIFY merchant_no varchar(64) DEFAULT NULL COMMENT '易宝商户号';
UPDATE pay_yeepay_merchant_account SET merchant_no = NULL WHERE merchant_no = '';
