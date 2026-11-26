-- Apply once, after member_youzan_import.sql. No member balance or snapshot is backfilled.
-- Disable claim writes before reverting the application; retain these receipt fields.
ALTER TABLE member_youzan_point_snapshot
    ADD COLUMN claimed_user_id BIGINT NULL,
    ADD COLUMN claimed_at DATETIME(6) NULL,
    ADD CONSTRAINT chk_youzan_claim_receipt CHECK (
        (restore_status = 0 AND claimed_user_id IS NULL AND claimed_at IS NULL)
        OR (restore_status = 1 AND claimed_user_id IS NOT NULL AND claimed_at IS NOT NULL)
    );
