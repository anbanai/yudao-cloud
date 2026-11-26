-- 商品规则前向迁移。适用于每次只接受一条 SQL 的 MySQL 8+ 控制台。
-- 备份并暂停商品、交易写入后，在应用连接的数据库中按顺序逐条提交。
-- 先用第 1 步识别已有列。第 2、4 步只执行缺少的列。遇到报错先核对结果。

-- 1. 核对连接库和已有列。分库部署时，在商品库和交易库分别执行本步。
SELECT DATABASE() AS current_database, VERSION() AS database_version,
       EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'product_spu') AS has_product_spu,
       EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = 'trade_order') AS has_trade_order;

SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'product_spu'
        AND column_name IN ('marketing_config', 'is_pre_sale', 'delivery_days',
                            'buy_limit', 'buy_limit_level', 'point_discount_rate'))
    OR (table_name = 'trade_order'
        AND column_name IN ('pre_sale_days', 'promised_delivery_time')))
ORDER BY table_name, column_name;

-- 2. 在 has_product_spu = 1 的库按缺项补列。每条 ALTER 分开提交。
ALTER TABLE product_spu
  ADD COLUMN marketing_config json DEFAULT NULL COMMENT 'Optional member/tag purchase policy';

ALTER TABLE product_spu
  ADD COLUMN is_pre_sale bit(1) DEFAULT b'0' COMMENT 'Presale product flag';

ALTER TABLE product_spu
  ADD COLUMN delivery_days int DEFAULT NULL COMMENT 'Delivery days after payment';

ALTER TABLE product_spu
  ADD COLUMN buy_limit int NOT NULL DEFAULT 0 COMMENT 'Cumulative purchase cap; zero means unlimited';

ALTER TABLE product_spu
  ADD COLUMN buy_limit_level varchar(8) NOT NULL DEFAULT 'ALL' COMMENT 'Eligible member tier';

ALTER TABLE product_spu
  ADD COLUMN point_discount_rate decimal(4,3) DEFAULT NULL COMMENT 'Max share of product payable price covered by points';

-- 3. 在商品库逐条创建两张分组表。
CREATE TABLE IF NOT EXISTS product_group (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(64) NOT NULL,
  storefront_visible bit(1) NOT NULL DEFAULT b'1',
  sort int NOT NULL DEFAULT 0,
  status tinyint NOT NULL DEFAULT 0,
  remark varchar(255) DEFAULT NULL,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_product_group_tenant_status_sort (tenant_id, status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_group_spu (
  id bigint NOT NULL AUTO_INCREMENT,
  group_id bigint NOT NULL,
  spu_id bigint NOT NULL,
  sort int NOT NULL DEFAULT 0,
  creator varchar(64) DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) DEFAULT '',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted bit(1) NOT NULL DEFAULT b'0',
  tenant_id bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_product_group_spu (tenant_id, group_id, spu_id),
  KEY idx_product_group_spu_group_sort (tenant_id, group_id, sort, id),
  KEY idx_product_group_spu_spu (tenant_id, spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 下单锁表建在交易服务使用的库。分库时先切换到交易库。
CREATE TABLE IF NOT EXISTS trade_purchase_lock (
  tenant_id bigint NOT NULL,
  user_id bigint NOT NULL,
  PRIMARY KEY (tenant_id, user_id)
) ENGINE=InnoDB;

-- 4. 在 has_trade_order = 1 的库按缺项补列。当前库没有 trade_order 时跳过这两条。
ALTER TABLE trade_order
  ADD COLUMN pre_sale_days int NOT NULL DEFAULT 0 COMMENT 'Presale period snapshot';

ALTER TABLE trade_order
  ADD COLUMN promised_delivery_time datetime DEFAULT NULL COMMENT 'Payment success plus presale days';

-- 5. 在 system_menu 所在库核对商品中心及分组菜单。209 应为有效的商品中心（path=product）。
-- 若 12732–12736 有其他用途、存在已删除的旧行，或同一菜单已使用其他 ID，先核对再执行 INSERT。
SELECT id, name, parent_id, path, component, permission, CAST(deleted AS UNSIGNED) AS deleted
FROM system_menu
WHERE id IN (209, 12732, 12733, 12734, 12735, 12736)
   OR (deleted = b'0'
       AND (component = 'mall/product/group/index'
            OR permission IN ('product:group:query', 'product:group:create',
                              'product:group:update', 'product:group:delete')))
ORDER BY id;

-- 已正确存在的行会跳过。若指定 ID 被占用，INSERT 会返回主键冲突。
INSERT INTO system_menu (
  id, name, permission, type, sort, parent_id, path, icon, component,
  component_name, status, visible, keep_alive, always_show,
  creator, create_time, updater, update_time, deleted
)
SELECT seed.id, seed.name, seed.permission, seed.type, seed.sort, seed.parent_id,
       seed.path, seed.icon, seed.component, seed.component_name,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM (
  SELECT 12732 AS id, '商品分组' AS name, '' AS permission, 2 AS type, 4 AS sort,
         209 AS parent_id, 'group' AS path, 'ep:collection-tag' AS icon,
         'mall/product/group/index' AS component, 'ProductGroup' AS component_name
  UNION ALL SELECT 12733, '分组查询', 'product:group:query', 3, 1, 12732, '', '', '', NULL
  UNION ALL SELECT 12734, '分组创建', 'product:group:create', 3, 2, 12732, '', '', '', NULL
  UNION ALL SELECT 12735, '分组更新', 'product:group:update', 3, 3, 12732, '', '', '', NULL
  UNION ALL SELECT 12736, '分组删除', 'product:group:delete', 3, 4, 12732, '', '', '', NULL
) AS seed
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu AS present
  WHERE present.id = seed.id AND present.type = seed.type
    AND present.parent_id = seed.parent_id AND present.permission = seed.permission
    AND (seed.id <> 12732 OR present.name = seed.name)
    AND present.path = seed.path AND present.component = seed.component
    AND present.deleted = b'0'
);

-- 6. 在对应库重跑第 1、5 步查询，核对 6 个商品字段、2 个订单字段和 5 个菜单项。
-- 下列查询在合库中返回 3 张表。分库时在商品库核对 2 张分组表，在交易库核对下单锁表。
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('product_group', 'product_group_spu', 'trade_purchase_lock')
ORDER BY table_name;
