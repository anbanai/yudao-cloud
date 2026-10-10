-- 商家旧明文 -> AES:v1 专用升级脚本（MySQL 8）。
-- 仅适用于从旧 HEAD 54cead8d6 的纯明文商家表升级的部署。
-- 已经部署 EncryptTypeHandler、存在无前缀 AES 或 AES:v1 密文的部署必须跳过本脚本。
-- 此脚本不判断解密失败、不推断数据格式。执行前备份，并停止商家写入。
-- 顺序：确保旧版商户/资质表存在，再执行本脚本，最后启动新版本。
-- 启动时临时设置：
-- yudao.product.merchant.encryption.allow-marked-plaintext=true
-- yudao.product.merchant.encryption.backfill-enabled=true
-- 启动回填将各租户（含软删除）显式 PLAIN:v0 数据逐批加密，断点续跑。
-- migration state=ENCRYPTED 后关闭以上两个开关；原 AES 密文不需要此迁移。
-- 不在 SQL、业务代码或日志中保存密钥；继续使用 mybatis-plus.encryptor.password。

CREATE TABLE IF NOT EXISTS product_merchant_encryption_migration (
    migration_id varchar(64) NOT NULL PRIMARY KEY,
    state varchar(32) NOT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='商家明文升级来源记录';

ALTER TABLE product_merchant
    MODIFY credit_code varchar(2048) DEFAULT NULL,
    MODIFY legal_person varchar(2048) DEFAULT NULL,
    MODIFY contact_name varchar(2048) DEFAULT NULL,
    MODIFY contact_mobile varchar(2048) DEFAULT NULL;
ALTER TABLE product_merchant_qualification
    MODIFY certificate_no varchar(2048) DEFAULT NULL,
    MODIFY file_url text NOT NULL;
-- 唯一版本记录保证重复执行不会给明文再次添加前缀或重新标记新密文。
DROP PROCEDURE IF EXISTS migrate_merchant_plaintext_v0;
DELIMITER $$
CREATE PROCEDURE migrate_merchant_plaintext_v0()
BEGIN
    DECLARE inserted_rows int;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    START TRANSACTION;
    INSERT IGNORE INTO product_merchant_encryption_migration(migration_id, state)
        VALUES ('merchant-plaintext-v0-to-aes-v1', 'MARKED');
    SET inserted_rows = ROW_COUNT();
    IF inserted_rows = 1 THEN
        UPDATE product_merchant SET credit_code = CONCAT('PLAIN:v0:', credit_code) WHERE credit_code IS NOT NULL;
        UPDATE product_merchant SET legal_person = CONCAT('PLAIN:v0:', legal_person) WHERE legal_person IS NOT NULL;
        UPDATE product_merchant SET contact_name = CONCAT('PLAIN:v0:', contact_name) WHERE contact_name IS NOT NULL;
        UPDATE product_merchant SET contact_mobile = CONCAT('PLAIN:v0:', contact_mobile) WHERE contact_mobile IS NOT NULL;
        UPDATE product_merchant_qualification SET certificate_no = CONCAT('PLAIN:v0:', certificate_no) WHERE certificate_no IS NOT NULL;
        UPDATE product_merchant_qualification SET file_url = CONCAT('PLAIN:v0:', file_url) WHERE file_url IS NOT NULL;
    END IF;
    COMMIT;
END$$
DELIMITER ;
CALL migrate_merchant_plaintext_v0();
DROP PROCEDURE migrate_merchant_plaintext_v0;
