-- 仅扩大原有商户敏感字段，不改写任何明文或密文。先备份；保持原有 AES 密钥。

-- 所有部署均需执行；纯明文来源另按 yeepay_merchant_encryption_migration.sql 标记。

ALTER TABLE product_merchant MODIFY credit_code varchar(2048) NULL;

ALTER TABLE product_merchant MODIFY legal_person varchar(2048) NULL;

ALTER TABLE product_merchant MODIFY contact_name varchar(2048) NULL;

ALTER TABLE product_merchant MODIFY contact_mobile varchar(2048) NULL;

ALTER TABLE product_merchant_qualification MODIFY certificate_no varchar(2048) NULL;

ALTER TABLE product_merchant_qualification MODIFY file_url text NOT NULL;
