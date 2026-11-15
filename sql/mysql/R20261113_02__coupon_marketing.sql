-- Stop all marketing/order writes, export these three tables and coupon/order snapshots first.
-- Refuse rollback while any exchange is awaiting fulfillment. Resolve pending exchange orders first.
DELIMITER $$
CREATE PROCEDURE cmp106_guard_rollback()
BEGIN
 IF EXISTS(SELECT 1 FROM promotion_coupon_exchange_record WHERE status=0 AND deleted=0) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Pending exchange orders must be resolved before rollback';
 END IF;
END$$
DELIMITER ;
CALL cmp106_guard_rollback();
DROP PROCEDURE cmp106_guard_rollback;
DROP TABLE promotion_coupon_promotion_take, promotion_coupon_exchange_record, promotion_coupon_promotion, promotion_coupon_send_batch;
ALTER TABLE promotion_coupon_template DROP COLUMN coupon_type, DROP COLUMN exchange_spu_id, DROP COLUMN exchange_sku_id,
 DROP COLUMN can_stack, DROP COLUMN stack_limit, DROP COLUMN stack_with_promotion, DROP COLUMN newcomer_only;
ALTER TABLE promotion_coupon DROP INDEX idx_coupon_source_user, DROP INDEX idx_coupon_used_recovery, DROP COLUMN coupon_type, DROP COLUMN exchange_spu_id, DROP COLUMN exchange_sku_id,
 DROP COLUMN can_stack, DROP COLUMN stack_limit, DROP COLUMN stack_with_promotion, DROP COLUMN promotion_id;
ALTER TABLE trade_order DROP COLUMN coupon_ids;
