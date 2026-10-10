-- 仅扩大原有商户敏感字段，不改写任何明文或密文。先备份；保持原有 AES 密钥。

-- 所有部署均需执行；纯明文来源另按 yeepay_merchant_encryption_migration.sql 标记。

ALTER TABLE product_merchant ALTER COLUMN credit_code TYPE varchar(2048);

ALTER TABLE product_merchant ALTER COLUMN legal_person TYPE varchar(2048);

ALTER TABLE product_merchant ALTER COLUMN contact_name TYPE varchar(2048);

ALTER TABLE product_merchant ALTER COLUMN contact_mobile TYPE varchar(2048);

ALTER TABLE product_merchant_qualification ALTER COLUMN certificate_no TYPE varchar(2048);

ALTER TABLE product_merchant_qualification ALTER COLUMN file_url TYPE text;
