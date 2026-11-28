-- Run after V20261125_01__product_youzan_import.sql on both existing and new installations.
ALTER TABLE product_yz_import_batch
  MODIFY merchant_id BIGINT NULL,
  ADD INDEX pending_batches (tenant_id, operator_id, merchant_id, confirmed, batch_id);
