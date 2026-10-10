-- 仅用于已确认原始纯明文的商家表；已加密部署严禁执行。先停写、备份、执行列宽脚本。

-- 建表执行一次；以下数据迁移事务受唯一版本账本保护，可单独重复执行。

-- 应用回填完成后关闭 allow-marked-plaintext/backfill-enabled。

CREATE TABLE product_merchant_encryption_migration (migration_id varchar(64) NOT NULL PRIMARY KEY, state varchar(64) NOT NULL, create_time timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL);

BEGIN;

UPDATE product_merchant SET credit_code = 'PLAIN:v0:' || credit_code WHERE credit_code IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_merchant_encryption_migration WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1');

UPDATE product_merchant SET legal_person = 'PLAIN:v0:' || legal_person WHERE legal_person IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_merchant_encryption_migration WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1');

UPDATE product_merchant SET contact_name = 'PLAIN:v0:' || contact_name WHERE contact_name IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_merchant_encryption_migration WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1');

UPDATE product_merchant SET contact_mobile = 'PLAIN:v0:' || contact_mobile WHERE contact_mobile IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_merchant_encryption_migration WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1');

UPDATE product_merchant_qualification SET certificate_no = 'PLAIN:v0:' || certificate_no WHERE certificate_no IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_merchant_encryption_migration WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1');

UPDATE product_merchant_qualification SET file_url = 'PLAIN:v0:' || file_url WHERE file_url IS NOT NULL AND NOT EXISTS (SELECT 1 FROM product_merchant_encryption_migration WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1');

INSERT INTO product_merchant_encryption_migration (migration_id, state) SELECT 'merchant-plaintext-v0-to-aes-v1', 'MARKED' WHERE NOT EXISTS (SELECT 1 FROM product_merchant_encryption_migration WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1');

COMMIT;
