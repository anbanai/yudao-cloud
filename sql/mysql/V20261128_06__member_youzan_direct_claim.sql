-- Apply after V20261128_04, cmp172/01-youzan-claim-receipt and cmp172/02-youzan-recovery-source.
-- Upgrade all member import/claim writers together. The member service converts confirmed status-3
-- batches automatically at startup using the existing application encryption key.
ALTER TABLE member_youzan_point_snapshot
    MODIFY COLUMN merchant_id BIGINT NULL,
    ADD COLUMN direct_mobile_hash CHAR(64) GENERATED ALWAYS AS
        (CASE WHEN merchant_id IS NULL THEN mobile_hash ELSE NULL END) VIRTUAL,
    ADD UNIQUE KEY uk_youzan_direct_mobile (tenant_id, direct_mobile_hash),
    ADD KEY idx_youzan_claim_mobile (tenant_id, mobile_hash, id);

ALTER TABLE member_youzan_import_batch
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0预览中 1已导入 2已过期 3旧批次待转换 4旧批次转换失败可重试';
