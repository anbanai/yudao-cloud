-- 商家旧明文 -> AES:v1 专用升级脚本（MySQL 8）。
-- 仅适用于从旧 HEAD 54cead8d6 的纯明文商家表升级的部署。
-- 已经部署 EncryptTypeHandler、存在无前缀 AES 或 AES:v1 密文的部署必须跳过本脚本。
-- 此脚本不判断解密失败、不推断数据格式。执行前备份，并停止商家写入及正在运行的回填任务。
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
-- 兼容不支持存储过程、客户端脚本分隔指令或一次提交多条语句的 MySQL 兼容执行器：
-- 下面每个 UPDATE/INSERT 都是独立语句，请按文件顺序逐条执行，不要把整个文件作为一个请求发送。
-- 迁移前必须停止商户写入并完成备份。账本 INSERT 放在最后，保证中断后可安全重跑未完成的步骤。
-- 每个 UPDATE 同时排除已有 PLAIN:v0:/AES:v1: 前缀，并仅在账本不是 ENCRYPTED 时执行；
-- 不会重复加前缀，旧脚本留下 MARKED 后也可以继续补齐未完成字段。
-- 已存在无前缀 AES 密文或 AES:v1 密文的部署必须跳过本脚本，不得把密文当作旧明文迁移。

UPDATE product_merchant
SET credit_code = CONCAT('PLAIN:v0:', credit_code)
WHERE credit_code IS NOT NULL
  AND credit_code NOT LIKE 'PLAIN:v0:%'
  AND credit_code NOT LIKE 'AES:v1:%'
  AND NOT EXISTS (
      SELECT 1 FROM product_merchant_encryption_migration
      WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1' AND state = 'ENCRYPTED'
  );

UPDATE product_merchant
SET legal_person = CONCAT('PLAIN:v0:', legal_person)
WHERE legal_person IS NOT NULL
  AND legal_person NOT LIKE 'PLAIN:v0:%'
  AND legal_person NOT LIKE 'AES:v1:%'
  AND NOT EXISTS (
      SELECT 1 FROM product_merchant_encryption_migration
      WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1' AND state = 'ENCRYPTED'
  );

UPDATE product_merchant
SET contact_name = CONCAT('PLAIN:v0:', contact_name)
WHERE contact_name IS NOT NULL
  AND contact_name NOT LIKE 'PLAIN:v0:%'
  AND contact_name NOT LIKE 'AES:v1:%'
  AND NOT EXISTS (
      SELECT 1 FROM product_merchant_encryption_migration
      WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1' AND state = 'ENCRYPTED'
  );

UPDATE product_merchant
SET contact_mobile = CONCAT('PLAIN:v0:', contact_mobile)
WHERE contact_mobile IS NOT NULL
  AND contact_mobile NOT LIKE 'PLAIN:v0:%'
  AND contact_mobile NOT LIKE 'AES:v1:%'
  AND NOT EXISTS (
      SELECT 1 FROM product_merchant_encryption_migration
      WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1' AND state = 'ENCRYPTED'
  );

UPDATE product_merchant_qualification
SET certificate_no = CONCAT('PLAIN:v0:', certificate_no)
WHERE certificate_no IS NOT NULL
  AND certificate_no NOT LIKE 'PLAIN:v0:%'
  AND certificate_no NOT LIKE 'AES:v1:%'
  AND NOT EXISTS (
      SELECT 1 FROM product_merchant_encryption_migration
      WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1' AND state = 'ENCRYPTED'
  );

UPDATE product_merchant_qualification
SET file_url = CONCAT('PLAIN:v0:', file_url)
WHERE file_url IS NOT NULL
  AND file_url NOT LIKE 'PLAIN:v0:%'
  AND file_url NOT LIKE 'AES:v1:%'
  AND NOT EXISTS (
      SELECT 1 FROM product_merchant_encryption_migration
      WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1' AND state = 'ENCRYPTED'
  );

-- 最后写入唯一迁移账本；重复执行时该 INSERT 不会新增记录，前面的 UPDATE 也会因账本存在而跳过。
INSERT INTO product_merchant_encryption_migration (migration_id, state)
SELECT 'merchant-plaintext-v0-to-aes-v1', 'MARKED'
WHERE NOT EXISTS (
    SELECT 1 FROM product_merchant_encryption_migration
    WHERE migration_id = 'merchant-plaintext-v0-to-aes-v1'
);
