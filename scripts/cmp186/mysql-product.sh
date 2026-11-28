#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${BYTE_BUDDY_AGENT:?Absolute path to byte-buddy-agent-1.17.8.jar}"
test -f "$BYTE_BUDDY_AGENT"
socket=${DOCKER_HOST:-unix:///var/run/docker.sock}
[[ "$socket" == unix:///* ]] || { echo 'Expected local Docker socket' >&2; exit 1; }
d() { docker --host "$socket" "$@"; }
work=$(mktemp -d); container=''
cleanup() { if [[ -n "$container" ]]; then d rm -fv "$container" >/dev/null; fi; rm -rf "$work"; }
trap cleanup EXIT
export MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24) CMP186_PRODUCT_PASSWORD=$(openssl rand -hex 24)
container=$(d run --pull=never --rm -d -p 127.0.0.1::3306 -e MYSQL_ROOT_PASSWORD -e MYSQL_DATABASE=cmp186_ephemeral mysql:8.0.44)
unset MYSQL_ROOT_PASSWORD
root() { d exec -i "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -NB cmp186_ephemeral "$@"' -- "$@"; }
app() { d exec -i -e MYSQL_PWD="$CMP186_PRODUCT_PASSWORD" "$container" mysql -h127.0.0.1 -uproduct_fixture -NB cmp186_ephemeral "$@"; }
for attempt in $(seq 1 90); do if root -e 'SELECT 1' >/dev/null 2>&1; then break; fi; sleep 1; done
test "$(root -e 'SELECT VERSION()')" = 8.0.44
node --input-type=module > "$work/schema.sql" <<'JS'
import fs from 'node:fs';
const lines=fs.readFileSync('db/yudao.sql','utf8').split('\n');
for (const table of ['system_menu','system_role_menu','product_brand','trade_delivery_express_template','member_user','member_level','member_point_record','member_level_record']) {
  const start=lines.findIndex(line=>line.startsWith('CREATE TABLE `'+table+'` '));
  const end=lines.findIndex((line,i)=>i>start && /^\)\s*ENGINE\s*=/i.test(line));
  const ddl=lines.slice(start,end+1).join('\n');
  if(start<0||end<=start||!ddl.trimEnd().endsWith(';')||/^(INSERT|UPDATE|DELETE|DROP|REPLACE)\b/im.test(ddl)) throw Error(table);
  console.log(ddl);
}
JS
root < "$work/schema.sql"
root -e "INSERT INTO system_menu(id,name,type,parent_id,path) VALUES (209,'synthetic',1,0,''),(210,'会员管理',2,0,'user'),(700,'会员中心',1,0,'/member'),(701,'商城系统',1,0,'/mall'),(702,'商品中心',1,701,'product')"
root < sql/mysql/member_youzan_import.sql
root < sql/mysql/cmp158/02-member-recovery-floor.sql
root < sql/mysql/cmp172/01-youzan-claim-receipt.sql
root < sql/mysql/cmp172/02-youzan-recovery-source.sql
root < sql/mysql/V20261125_01__product_youzan_import.sql
test "$(root -e "SELECT GROUP_CONCAT(parent_id ORDER BY parent_id) FROM system_menu WHERE component IN ('member/youzan-import/index','mall/product/youzan-import/index')")" = '0,209'
root -e "INSERT INTO system_role_menu(role_id,menu_id,tenant_id) SELECT 13,id,1 FROM system_menu WHERE component IN ('member/youzan-import/index','mall/product/youzan-import/index')"
menus=sql/mysql/V20261128_01__youzan_import_menus.sql
root < "$menus"
root < "$menus"
test "$(root -e "SELECT GROUP_CONCAT(parent_id ORDER BY parent_id) FROM system_menu WHERE component IN ('member/youzan-import/index','mall/product/youzan-import/index')")" = '700,702'
test "$(root -e 'SELECT COUNT(*) FROM system_role_menu WHERE role_id=13 AND tenant_id=1')" = 5
test "$(root -e "SELECT keep_alive+0 FROM system_menu WHERE component='mall/product/youzan-import/index'")" = 1
brand=sql/mysql/cmp158/01-brand-detail.sql
root < "$brand"
root -e "INSERT INTO product_brand(name,pic_url,sort,status) VALUES('synthetic','fixture.png',1,0)"
test "$(root -e 'SELECT show_in_detail+0 FROM product_brand')" = 0
root -e 'UPDATE product_brand SET show_in_detail=1; ALTER TABLE product_brand RENAME COLUMN show_in_detail TO show_on_detail'
root < "$brand"; root < "$brand"
test "$(root -e 'SELECT show_in_detail+0 FROM product_brand')" = 1
root -e 'ALTER TABLE product_brand ADD COLUMN show_on_detail BOOLEAN NOT NULL DEFAULT 0'
if root < "$brand" > "$work/dual" 2>&1; then exit 1; fi
grep -q 'Both brand columns exist' "$work/dual"
test "$(root -e 'SELECT CONCAT(show_in_detail+0,show_on_detail+0) FROM product_brand')" = 10
root <<SQL
CREATE TABLE product_merchant(id BIGINT,tenant_id BIGINT,deleted INT, PRIMARY KEY(id,tenant_id));
CREATE TABLE product_spu(id BIGINT PRIMARY KEY,merchant_id BIGINT,tenant_id BIGINT,deleted INT);
CREATE TABLE product_sku(id BIGINT PRIMARY KEY,spu_id BIGINT,tenant_id BIGINT,price INT,stock INT,deleted INT);
INSERT INTO product_merchant VALUES(2,1,0);
INSERT INTO product_yz_import_grant VALUES(1,2,7);
INSERT INTO trade_delivery_express_template(id,name,sort,charge_mode,tenant_id,deleted) VALUES(10,'Synthetic',0,1,1,0),(11,'Other tenant',0,1,5,0),(12,'Deleted',0,1,1,1),(13,'Unsupported',0,9,1,0);
CREATE USER 'product_fixture'@'%' IDENTIFIED BY '$CMP186_PRODUCT_PASSWORD';
GRANT LOCK TABLES ON cmp186_ephemeral.* TO 'product_fixture'@'%';
SQL
for table in product_merchant trade_delivery_express_template product_yz_import_grant; do root -e "GRANT SELECT ON cmp186_ephemeral.$table TO 'product_fixture'@'%'"; done
for table in product_yz_import_batch product_yz_import_draft product_spu product_sku; do root -e "GRANT SELECT,INSERT,UPDATE ON cmp186_ephemeral.$table TO 'product_fixture'@'%'"; done
if app -e 'UPDATE trade_delivery_express_template SET name=name WHERE id=10' > "$work/denied" 2>&1; then exit 1; fi
grep -q 'ERROR 1142' "$work/denied"
lock='SELECT id FROM trade_delivery_express_template WHERE id=10 AND tenant_id=1 AND deleted=0 AND charge_mode IN (1,2,3) FOR UPDATE'
app -e "BEGIN; $lock; SELECT GET_LOCK('cmp186_ready',0); SELECT SLEEP(7); ROLLBACK" > "$work/holder" & holder=$!
for attempt in $(seq 1 40); do if [[ "$(root -e "SELECT IS_USED_LOCK('cmp186_ready') IS NOT NULL")" = 1 ]]; then break; fi; sleep .1; done
test "$(root -e "SELECT IS_USED_LOCK('cmp186_ready') IS NOT NULL")" = 1
if app -e "SET innodb_lock_wait_timeout=1; BEGIN; $lock" > "$work/wait" 2>&1; then exit 1; fi
grep -q 'ERROR 1205' "$work/wait"; wait "$holder"
test "$(app -e "BEGIN; $lock; ROLLBACK")" = 10
export CMP186_PRODUCT_MYSQL_PORT=$(d port "$container" 3306/tcp | sed -n 's/^127\.0\.0\.1://p')
[[ "$CMP186_PRODUCT_MYSQL_PORT" =~ ^[0-9]+$ ]]
mod=yudao-module-mall/yudao-module-product-server
report="$mod/target/surefire-reports/TEST-cn.iocoder.yudao.module.product.service.youzan.YouzanBatchTest.xml"
rm -f "$report"
mvn -B -ntp "$@" -pl "$mod" -am -Dflatten.skip=true -Dtest=YouzanBatchTest#invalidDeliveryKeepsDraftUntilExpressTemplateIsValid -Dsurefire.failIfNoSpecifiedTests=false "-DargLine=-javaagent:$BYTE_BUDDY_AGENT -Djava.io.tmpdir=$work" test
node -e 'const xml=require("fs").readFileSync(process.argv[1],"utf8").match(/<testsuite\b[^>]*>/)?.[0]||"";for(const [k,v] of Object.entries({tests:1,errors:0,skipped:0,failures:0}))if(!xml.includes(` ${k}="${v}"`))throw Error(k)' "$report"
root -e "UPDATE system_menu SET status=1,visible=0 WHERE permission IN ('product:youzan:import','member:youzan-import:query','member:youzan-import:import') OR component='member/youzan-import/index'; DELETE FROM product_yz_import_grant WHERE tenant_id=1 AND merchant_id=2 AND operator_id=7;"
test "$(app -e 'SELECT COUNT(*) FROM product_yz_import_grant')" = 0
test "$(app -e 'SELECT COUNT(*) FROM product_yz_import_draft WHERE mall_sku_id=202')" = 1
test "$(app -e 'SELECT CONCAT(price,CHAR(58),stock) FROM product_sku WHERE id=202')" = 825:7
test "$(root -e 'SELECT show_in_detail+0 FROM product_brand')" = 1
echo 'PASS: synthetic migrations, brand compatibility, locking/denial, service rollback, disable and retained data'
