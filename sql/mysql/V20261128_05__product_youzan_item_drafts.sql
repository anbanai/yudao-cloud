-- Existing unassigned batches are opened from their encrypted snapshot on first edit.
ALTER TABLE product_yz_import_batch
  ADD COLUMN all_products_published BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE product_yz_import_item (
  tenant_id BIGINT NOT NULL,
  batch_id CHAR(36) CHARACTER SET ascii NOT NULL,
  product_key VARCHAR(80) NOT NULL,
  payload MEDIUMBLOB NOT NULL,
  merchant_id BIGINT NULL,
  spu_id BIGINT NULL,
  PRIMARY KEY (tenant_id, batch_id, product_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
