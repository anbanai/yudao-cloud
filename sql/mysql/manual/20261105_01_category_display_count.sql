-- 在商品服务连接的 MySQL 库中按顺序逐条执行。
-- 1. 核对当前库和商品分类表。返回 0 表示对应对象不存在。
SELECT DATABASE() AS current_database,
       EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'product_category') AS has_product_category,
       EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'product_category'
                 AND column_name = 'display_product_count') AS has_display_product_count;

-- 2. 仅当 has_product_category = 1 且 has_display_product_count = 0 时执行。
ALTER TABLE `product_category`
    ADD COLUMN `display_product_count` int NOT NULL DEFAULT 6 COMMENT '小程序展示商品数量' AFTER `pic_url`;

-- 3. 新增列后或列已存在时执行，修正无效的展示数量。
UPDATE `product_category`
SET `display_product_count` = 6
WHERE `display_product_count` IS NULL OR `display_product_count` < 1 OR `display_product_count` > 50;
