CREATE TABLE product_yz_import_grant (
  tenant_id BIGINT NOT NULL, merchant_id BIGINT NOT NULL, operator_id BIGINT NOT NULL,
  PRIMARY KEY (tenant_id, merchant_id, operator_id)
) ENGINE=InnoDB;

CREATE TABLE product_yz_import_batch (
  batch_id CHAR(36) CHARACTER SET ascii PRIMARY KEY,
  tenant_id BIGINT NOT NULL, merchant_id BIGINT NOT NULL, operator_id BIGINT NOT NULL,
  confirmed BOOLEAN NOT NULL DEFAULT FALSE, expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), confirmed_at DATETIME(6),
  row_count INT NOT NULL, draft_count INT NOT NULL DEFAULT 0, payload MEDIUMBLOB NOT NULL,
  KEY owner_batches (tenant_id, merchant_id, operator_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE product_yz_import_draft (
  tenant_id BIGINT NOT NULL, merchant_id BIGINT NOT NULL,
  product_key VARCHAR(80) NOT NULL, sku_key VARCHAR(80) NOT NULL,
  name VARCHAR(255) NOT NULL, specification VARCHAR(512) NOT NULL,
  source_price INT NULL, source_stock INT NULL, mapping_hints TEXT NOT NULL,
  mall_sku_id BIGINT, batch_id CHAR(36) CHARACTER SET ascii NOT NULL,
  PRIMARY KEY (tenant_id, merchant_id, product_key, sku_key),
  UNIQUE KEY mapped_sku (tenant_id, merchant_id, mall_sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;



INSERT INTO system_menu (name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
VALUES ('有赞商品导入','product:youzan:import',2,10,209,'youzan-import','ep:upload','mall/product/youzan-import/index','ProductYouzanImport',0,b'1',b'0',b'1','',NOW(),'',NOW(),b'0');
