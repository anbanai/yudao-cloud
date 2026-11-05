ALTER TABLE `product_category`
    ADD COLUMN IF NOT EXISTS `display_product_count` int NOT NULL DEFAULT 6 COMMENT '小程序展示商品数量' AFTER `pic_url`;

UPDATE `product_category`
SET `display_product_count` = 6
WHERE `display_product_count` IS NULL OR `display_product_count` < 1 OR `display_product_count` > 50;
