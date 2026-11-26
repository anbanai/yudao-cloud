ALTER TABLE member_point_record
    ADD COLUMN youzan_recovery_source VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        GENERATED ALWAYS AS (CASE WHEN biz_type = 3 THEN biz_id ELSE NULL END) VIRTUAL,
    ADD UNIQUE INDEX uk_youzan_recovery_source (tenant_id, youzan_recovery_source);
