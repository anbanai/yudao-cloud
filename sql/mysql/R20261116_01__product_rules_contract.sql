-- Stop product/trade writes, stop order creation, and export relations and fulfillment snapshots first.
DELIMITER $$
DROP PROCEDURE IF EXISTS cmp113_contract_rollback$$
CREATE PROCEDURE cmp113_contract_rollback()
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='pre_sale_days')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='promised_delivery_time') THEN
    IF EXISTS (SELECT 1 FROM trade_order WHERE pre_sale_days>0 OR promised_delivery_time IS NOT NULL) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Export pre-sale order snapshots before rollback';
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='pre_sale_days') THEN ALTER TABLE trade_order DROP COLUMN pre_sale_days; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='trade_order' AND column_name='promised_delivery_time') THEN ALTER TABLE trade_order DROP COLUMN promised_delivery_time; END IF;
  DROP TABLE IF EXISTS trade_purchase_lock;
  DROP TABLE IF EXISTS product_group_spu;
  DROP TABLE IF EXISTS product_group;
  DELETE FROM system_menu WHERE id IN (12733,12734,12735,12736) AND parent_id=12732
    AND permission IN ('product:group:query','product:group:create','product:group:update','product:group:delete');
  DELETE FROM system_menu WHERE id=12732 AND name='商品分组' AND path='group' AND parent_id=209;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='point_discount_rate') THEN ALTER TABLE product_spu DROP COLUMN point_discount_rate; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='buy_limit_level') THEN ALTER TABLE product_spu DROP COLUMN buy_limit_level; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='buy_limit') THEN ALTER TABLE product_spu DROP COLUMN buy_limit; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='delivery_days') THEN ALTER TABLE product_spu DROP COLUMN delivery_days; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='is_pre_sale') THEN ALTER TABLE product_spu DROP COLUMN is_pre_sale; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_spu' AND column_name='marketing_config') THEN ALTER TABLE product_spu DROP COLUMN marketing_config; END IF;
END$$
CALL cmp113_contract_rollback()$$
DROP PROCEDURE cmp113_contract_rollback$$
DELIMITER ;
