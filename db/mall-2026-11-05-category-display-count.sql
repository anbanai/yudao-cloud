-- 兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL 版本，并允许重复执行。
SET @sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'product_category'
          AND column_name = 'display_product_count'
    ),
    'SELECT 1',
    'ALTER TABLE `product_category` ADD COLUMN `display_product_count` int NOT NULL DEFAULT 6 COMMENT ''小程序展示商品数量'' AFTER `pic_url`'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `product_category`
SET `display_product_count` = 6
WHERE `display_product_count` IS NULL OR `display_product_count` < 1 OR `display_product_count` > 50;
