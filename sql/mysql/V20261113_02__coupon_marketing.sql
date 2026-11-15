-- MySQL 8.0+, run with writes paused; re-running is safe after interruption.
DELIMITER $$
DROP PROCEDURE IF EXISTS cmp106_add_column$$
CREATE PROCEDURE cmp106_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN definition TEXT)
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) THEN
  SET @cmp106_ddl=CONCAT('ALTER TABLE `',t,'` ADD COLUMN `',c,'` ',definition);
  PREPARE cmp106_stmt FROM @cmp106_ddl; EXECUTE cmp106_stmt; DEALLOCATE PREPARE cmp106_stmt;
 END IF;
END$$
DROP PROCEDURE IF EXISTS cmp106_add_index$$
CREATE PROCEDURE cmp106_add_index(IN t VARCHAR(64), IN i VARCHAR(64), IN definition TEXT)
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=t AND index_name=i) THEN
  SET @cmp106_ddl=CONCAT('ALTER TABLE `',t,'` ADD INDEX `',i,'` ',definition);
  PREPARE cmp106_stmt FROM @cmp106_ddl; EXECUTE cmp106_stmt; DEALLOCATE PREPARE cmp106_stmt;
 END IF;
END$$
DELIMITER ;
CALL cmp106_add_column('promotion_coupon_template','coupon_type','int NOT NULL DEFAULT 1');
CALL cmp106_add_column('promotion_coupon_template','exchange_spu_id','bigint NULL');
CALL cmp106_add_column('promotion_coupon_template','exchange_sku_id','bigint NULL');
CALL cmp106_add_column('promotion_coupon_template','can_stack','bit NOT NULL DEFAULT 0');
CALL cmp106_add_column('promotion_coupon_template','stack_limit','int NOT NULL DEFAULT 1');
CALL cmp106_add_column('promotion_coupon_template','stack_with_promotion','bit NOT NULL DEFAULT 0');
CALL cmp106_add_column('promotion_coupon','coupon_type','int NOT NULL DEFAULT 1');
CALL cmp106_add_column('promotion_coupon','exchange_spu_id','bigint NULL');
CALL cmp106_add_column('promotion_coupon','exchange_sku_id','bigint NULL');
CALL cmp106_add_column('promotion_coupon','can_stack','bit NOT NULL DEFAULT 0');
CALL cmp106_add_column('promotion_coupon','stack_limit','int NOT NULL DEFAULT 1');
CALL cmp106_add_column('promotion_coupon','stack_with_promotion','bit NOT NULL DEFAULT 0');
CALL cmp106_add_column('promotion_coupon_template','newcomer_only','bit NOT NULL DEFAULT 0');
CALL cmp106_add_column('promotion_coupon','promotion_id','bigint NULL');
CALL cmp106_add_column('trade_order','coupon_ids','varchar(512) NULL');
CREATE TABLE IF NOT EXISTS promotion_coupon_send_batch (
id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
deleted bit NOT NULL DEFAULT 0, tenant_id bigint NOT NULL DEFAULT 0,
template_id bigint NOT NULL, operator_id bigint NOT NULL, status int NOT NULL DEFAULT 0,
idempotency_key varchar(64) NOT NULL, total_count int NOT NULL DEFAULT 0,
success_count int NOT NULL DEFAULT 0, failure_count int NOT NULL DEFAULT 0,
mobile_snapshot mediumtext, user_snapshot mediumtext, failure_details mediumtext, template_snapshot text,
UNIQUE KEY uk_batch_request(tenant_id,idempotency_key)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS promotion_coupon_promotion (
id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
deleted bit NOT NULL DEFAULT 0, tenant_id bigint NOT NULL DEFAULT 0,
template_id bigint NOT NULL, source varchar(128), channel varchar(64), short_code varchar(64) NOT NULL,
mini_program_path varchar(512) NOT NULL, visit_count int NOT NULL DEFAULT 0, take_count int NOT NULL DEFAULT 0,
idempotency_key varchar(64) NOT NULL,
UNIQUE KEY uk_promotion_request(tenant_id,idempotency_key), UNIQUE KEY uk_promotion_code(tenant_id,short_code)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS promotion_coupon_exchange_record (
id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
deleted bit NOT NULL DEFAULT 0, tenant_id bigint NOT NULL DEFAULT 0,
coupon_id bigint NOT NULL,user_id bigint NOT NULL,spu_id bigint NOT NULL,sku_id bigint NOT NULL,
status int NOT NULL DEFAULT 0,order_id bigint NOT NULL,fulfilled_time datetime NULL,cancel_time datetime NULL,
idempotency_key varchar(64) NOT NULL,
UNIQUE KEY uk_exchange_request(tenant_id,idempotency_key), UNIQUE KEY uk_exchange_order_coupon(tenant_id,order_id,coupon_id)
) ENGINE=InnoDB;
CALL cmp106_add_index('promotion_coupon','idx_coupon_source_user','(tenant_id,promotion_id,user_id)');
CALL cmp106_add_index('promotion_coupon','idx_coupon_used_recovery','(tenant_id,status,use_time,id)');
DROP PROCEDURE cmp106_add_column;
DROP PROCEDURE cmp106_add_index;

CREATE TABLE IF NOT EXISTS promotion_coupon_promotion_take (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,promotion_id bigint NOT NULL,user_id bigint NOT NULL,coupon_id bigint NOT NULL,
 creator varchar(64) DEFAULT '',create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updater varchar(64) DEFAULT '',update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted bit NOT NULL DEFAULT 0,tenant_id bigint NOT NULL DEFAULT 0,
 UNIQUE KEY uk_source_user(tenant_id,promotion_id,user_id)
) ENGINE=InnoDB;
