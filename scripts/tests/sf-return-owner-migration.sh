#!/usr/bin/env bash
# 从仓库根目录运行。参数原样传给 mysql，例如 --socket=/path/to/mysql.sock -uroot。
set -euo pipefail

db="sf_return_owner_test_$$_${RANDOM}"
client=(mysql --no-defaults "$@" --batch --skip-column-names)
migration=sql/mysql/trade_after_sale_return_shipment.sql

"${client[@]}" -e "CREATE DATABASE $db"
trap '"${client[@]}" -e "DROP DATABASE $db"' EXIT
run_sql() { "${client[@]}" "$db" "$@"; }

# 在多商家迁移之前执行也必须成功。
run_sql < "$migration" > /dev/null
run_sql <<'SQL'
ALTER TABLE trade_after_sale_return_shipment DROP COLUMN merchant_id, DROP COLUMN monthly_card, DROP COLUMN service_code;
CREATE TABLE trade_after_sale (id bigint PRIMARY KEY, order_item_id bigint, order_id bigint, tenant_id bigint, deleted bit);
CREATE TABLE trade_order_item (id bigint PRIMARY KEY, sub_order_id bigint, order_id bigint, tenant_id bigint, merchant_id bigint, deleted bit);
CREATE TABLE trade_order_sub (id bigint PRIMARY KEY, order_id bigint, tenant_id bigint, merchant_id bigint, deleted bit);
INSERT INTO trade_after_sale VALUES
  (1,1,10,1,0),(2,2,10,1,0),(3,3,10,1,0),(4,4,10,1,0),(5,5,10,1,0),(6,6,10,1,0),(7,7,10,1,0);
INSERT INTO trade_order_item VALUES
  (1,11,10,1,NULL,0),(2,12,10,1,42,0),(3,13,10,1,42,0),(4,14,11,1,42,0),
  (5,15,10,1,42,0),(6,16,10,1,42,0),(7,17,10,1,42,0);
INSERT INTO trade_order_sub VALUES
  (11,10,1,42,0),(12,10,2,42,0),(13,10,1,99,0),(14,11,1,42,0),
  (15,10,1,42,1),(16,10,1,42,0),(17,10,1,42,0);
INSERT INTO trade_after_sale_return_shipment (after_sale_id,tenant_id,provider,status,idempotency_key,warehouse_address)
  SELECT id,IF(id=7,2,1),'SF','CREATE_FAILED',CAST(id AS CHAR),'original-address' FROM trade_after_sale;
SQL

run_sql < "$migration" > /dev/null
# 已有归属和预约快照必须保留。
run_sql -e "UPDATE trade_after_sale_return_shipment SET merchant_id=77 WHERE after_sale_id=6"
for pass in 1 2; do
  run_sql < "$migration" > /dev/null
  result=$(run_sql -e "SELECT GROUP_CONCAT(COALESCE(merchant_id,'NULL') ORDER BY after_sale_id),
                        SUM(warehouse_address='original-address'), SUM(monthly_card IS NULL)
                       FROM trade_after_sale_return_shipment")
  expected=$'42,NULL,NULL,NULL,NULL,77,NULL\t7\t7'
  if [[ "$result" != "$expected" ]]; then
    echo "Unexpected migration result on pass $pass: $result" >&2
    exit 1
  fi
done
echo "SF return-owner migration passed"
