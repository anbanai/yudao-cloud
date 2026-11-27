-- Run with import/claim writers stopped, after member_youzan_import.sql, without --force.
-- Existing receipt layouts are deliberately not auto-migrated: retain evidence for review.
DROP PROCEDURE IF EXISTS cmp172_install_claim_receipt;
DELIMITER //
CREATE PROCEDURE cmp172_install_claim_receipt()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                   AND table_name = 'member_youzan_point_snapshot' AND engine = 'InnoDB') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected InnoDB import snapshot table';
    END IF;
    IF NOT EXISTS (
        SELECT index_name FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'member_youzan_point_snapshot'
        GROUP BY index_name HAVING MAX(non_unique) = 0 AND COUNT(*) = 3
          AND COUNT(sub_part) = 0 AND COUNT(column_name) = 3
          AND GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'tenant_id,merchant_id,mobile_hash'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing exact unique tenant/merchant/mobile index';
    END IF;
    IF EXISTS (SELECT 1 FROM member_youzan_point_snapshot GROUP BY tenant_id, merchant_id, mobile_hash
               HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate snapshot keys: no migration performed';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
               AND table_name = 'member_youzan_point_snapshot' AND column_name IN ('claimed_user_id','claimed_at')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing receipt layout: audit columns, values and constraints separately';
    END IF;
    IF EXISTS (SELECT 1 FROM member_youzan_point_snapshot WHERE restore_status IS NULL OR restore_status <> 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Historical non-pending snapshot without receipts: stop, do not backfill';
    END IF;
    ALTER TABLE member_youzan_point_snapshot
        ADD COLUMN claimed_user_id BIGINT NULL,
        ADD COLUMN claimed_at DATETIME(6) NULL,
        ADD CONSTRAINT chk_youzan_claim_receipt CHECK (restore_status IS NOT NULL AND (
            (restore_status = 0 AND claimed_user_id IS NULL AND claimed_at IS NULL)
            OR (restore_status = 1 AND claimed_user_id IS NOT NULL AND claimed_at IS NOT NULL)
        ));
END//
DELIMITER ;
CALL cmp172_install_claim_receipt();
DROP PROCEDURE cmp172_install_claim_receipt;
