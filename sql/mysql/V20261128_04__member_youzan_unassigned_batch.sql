-- Apply to the member database after member_youzan_import.sql.
ALTER TABLE `member_youzan_import_batch`
    MODIFY COLUMN `merchant_id` bigint DEFAULT NULL,
    MODIFY COLUMN `expires_at` datetime DEFAULT NULL,
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '0预览中 1已导入 2已过期 3待分配会员来源';
