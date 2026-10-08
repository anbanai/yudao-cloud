-- 商品分组运营升级。首次使用分组请先执行 product_group.sql。
-- 在部署新版后端之前执行；历史分组默认公开，重复执行保留已有设置。
SET @product_group_visibility_ddl = IF(
  EXISTS (SELECT 1 FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_group' AND COLUMN_NAME = 'storefront_visible'),
  'SELECT 1',
  'ALTER TABLE `product_group` ADD COLUMN `storefront_visible` bit(1) NOT NULL DEFAULT b''1'' COMMENT ''是否在商城公开'''
);
PREPARE product_group_visibility_stmt FROM @product_group_visibility_ddl;
EXECUTE product_group_visibility_stmt;
DEALLOCATE PREPARE product_group_visibility_stmt;
