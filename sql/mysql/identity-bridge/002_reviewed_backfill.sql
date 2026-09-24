-- Run only during a login write freeze after importing a COMPLETE frozen legacy inventory.
-- source_bind_id is the original system_social_user_bind.id. Import active bindings; retain deleted history in the immutable audit export.
-- Set verdict='APPROVED' and scope='<tenant-id>:<app-id>:<backend-instance>' only using independent deployment evidence.
-- Ambiguous app / conflicting membership rows remain UNRESOLVED. Existing memberships are never deleted.
-- A separate approver must reconcile inventory count with the source database export BEFORE this procedure.
DELIMITER $$
CREATE PROCEDURE finalize_identity_scope(IN target_scope VARCHAR(192), IN expected_inventory BIGINT, IN actor VARCHAR(128))
BEGIN
  DECLARE actual_inventory BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  SELECT COUNT(*) INTO actual_inventory FROM member_identity_legacy_review;
  IF expected_inventory < 0 OR actual_inventory <> expected_inventory THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Incomplete historical identity inventory';
  END IF;
  IF EXISTS(SELECT 1 FROM member_identity_legacy_review WHERE verdict <> 'APPROVED' OR scope IS NULL OR evidence IS NULL OR evidence='') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ambiguous historical app ownership; migration blocked';
  END IF;
  IF EXISTS(SELECT 1 FROM member_identity_legacy_review WHERE scope=target_scope GROUP BY openid HAVING COUNT(DISTINCT member_id)>1)
    OR EXISTS(SELECT 1 FROM member_identity_legacy_review WHERE scope=target_scope GROUP BY member_id HAVING COUNT(DISTINCT openid)>1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Conflicting memberships; manual verified recovery required';
  END IF;
  START TRANSACTION;
  INSERT INTO member_identity_scope(scope,ready) VALUES(target_scope,0)
    ON DUPLICATE KEY UPDATE ready=0;
  INSERT INTO member_identity_account(scope,openid,member_id)
    SELECT DISTINCT scope,openid,member_id FROM member_identity_legacy_review WHERE scope=target_scope;
  -- Deliberately do not copy mobile or mark historical phone verified.
  UPDATE member_identity_scope SET ready=1,audited_at=UNIX_TIMESTAMP()*1000,audited_by=actor WHERE scope=target_scope;
  COMMIT;
END$$
DELIMITER ;
-- Call explicitly with the frozen source export count, including zero only for a proven new installation.
-- CALL finalize_identity_scope('<tenant-id>:<app-id>:<backend-instance>', <source-export-count>, '<reviewer>');
