-- CMP-113 rules adapted to the admin top-level ProductSpu contract.
-- Stop product/trade writes and back up before applying on MySQL 8+.
DELIMITER $$
DROP PROCEDURE IF EXISTS cmp113_contract_forward$$
CREATE PROCEDURE cmp113_contract_forward()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='marketing_config') THEN
    ALTER TABLE product_spu ADD COLUMN marketing_config json DEFAULT NULL COMMENT 'Optional member/tag purchase policy';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='is_pre_sale') THEN
    ALTER TABLE product_spu ADD COLUMN is_pre_sale bit(1) DEFAULT b'0' COMMENT 'Presale product flag';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='delivery_days') THEN
    ALTER TABLE product_spu ADD COLUMN delivery_days int DEFAULT NULL COMMENT 'Delivery days after payment';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='buy_limit') THEN
    ALTER TABLE product_spu ADD COLUMN buy_limit int NOT NULL DEFAULT 0 COMMENT 'Cumulative purchase cap; zero means unlimited';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='buy_limit_level') THEN
    ALTER TABLE product_spu ADD COLUMN buy_limit_level varchar(8) NOT NULL DEFAULT 'ALL' COMMENT 'Eligible member tier';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='point_discount_rate') THEN
    ALTER TABLE product_spu ADD COLUMN point_discount_rate decimal(4,3) DEFAULT NULL COMMENT 'Max share of product payable price covered by points';
  END IF;
  CREATE TABLE IF NOT EXISTS product_group (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(64) NOT NULL,
    storefront_visible bit(1) NOT NULL DEFAULT b'1',
    sort int NOT NULL DEFAULT 0,
    status tinyint NOT NULL DEFAULT 0,
    remark varchar(255) DEFAULT NULL,
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(id), KEY idx_product_group_tenant_status_sort(tenant_id,status,sort)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  CREATE TABLE IF NOT EXISTS product_group_spu (
    id bigint NOT NULL AUTO_INCREMENT,
    group_id bigint NOT NULL, spu_id bigint NOT NULL, sort int NOT NULL DEFAULT 0,
    creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(id), UNIQUE KEY uk_product_group_spu(tenant_id,group_id,spu_id),
    KEY idx_product_group_spu_group_sort(tenant_id,group_id,sort,id), KEY idx_product_group_spu_spu(tenant_id,spu_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  CREATE TABLE IF NOT EXISTS trade_purchase_lock (
    tenant_id bigint NOT NULL, user_id bigint NOT NULL, PRIMARY KEY(tenant_id,user_id)
  ) ENGINE=InnoDB;
  IF EXISTS (SELECT 1 FROM system_menu WHERE id=12732 AND (path<>'group' OR name<>'商品分组' OR parent_id<>209) AND deleted=b'0')
     OR EXISTS (SELECT 1 FROM system_menu WHERE id IN (12733,12734,12735,12736) AND deleted=b'0'
       AND NOT (parent_id=12732 AND permission IN ('product:group:query','product:group:create','product:group:update','product:group:delete'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Menu id 12732 is occupied; adjust product-group menu ids before rollout';
  END IF;
  INSERT IGNORE INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
    VALUES (12732,'商品分组','',2,4,209,'group','ep:collection-tag','mall/product/group/index','ProductGroup',0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
           (12733,'分组查询','product:group:query',3,1,12732,'','','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
           (12734,'分组创建','product:group:create',3,2,12732,'','','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
           (12735,'分组更新','product:group:update',3,3,12732,'','','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0'),
           (12736,'分组删除','product:group:delete',3,4,12732,'','','',NULL,0,b'1',b'1',b'1','1',NOW(),'1',NOW(),b'0');
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='trade_order') THEN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='pre_sale_days') THEN
      ALTER TABLE trade_order ADD COLUMN pre_sale_days int NOT NULL DEFAULT 0 COMMENT 'Presale period snapshot';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='promised_delivery_time') THEN
      ALTER TABLE trade_order ADD COLUMN promised_delivery_time datetime DEFAULT NULL COMMENT 'Payment success plus presale days';
    END IF;
  END IF;
END$$
CALL cmp113_contract_forward()$$
DROP PROCEDURE cmp113_contract_forward$$
DELIMITER ;
