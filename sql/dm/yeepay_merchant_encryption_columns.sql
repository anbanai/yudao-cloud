-- 仅扩大原有商户敏感字段，不改写任何明文或密文。先备份；保持原有 AES 密钥。

-- 所有部署均需执行；纯明文来源另按 yeepay_merchant_encryption_migration.sql 标记。

ALTER TABLE product_merchant MODIFY (credit_code varchar(2048));

ALTER TABLE product_merchant MODIFY (legal_person varchar(2048));

ALTER TABLE product_merchant MODIFY (contact_name varchar(2048));

ALTER TABLE product_merchant MODIFY (contact_mobile varchar(2048));

ALTER TABLE product_merchant_qualification MODIFY (certificate_no varchar(2048));

-- 以下 CLOB 转换执行一次，必须停止商家写入；保留原字段的 NOT NULL 约束。
ALTER TABLE product_merchant_qualification ADD (file_url_clob_migration clob);
UPDATE product_merchant_qualification SET file_url_clob_migration = file_url;
COMMIT;
ALTER TABLE product_merchant_qualification DROP COLUMN file_url;
ALTER TABLE product_merchant_qualification RENAME COLUMN file_url_clob_migration TO file_url;
ALTER TABLE product_merchant_qualification MODIFY (file_url NOT NULL);
