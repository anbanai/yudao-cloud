#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${BYTE_BUDDY_AGENT:?Set to the absolute path of byte-buddy-agent-1.17.8.jar}"
test -f "$BYTE_BUDDY_AGENT"
socket=${DOCKER_HOST:-unix:///var/run/docker.sock}
case "$socket" in unix:///*) ;; *) echo 'Only a local Docker socket is allowed' >&2; exit 1;; esac
docker_local() { docker --host "$socket" "$@"; }
work=$(mktemp -d)
container=''
cleanup() { if [[ -n "$container" ]]; then docker_local rm -fv "$container" >/dev/null; fi; rm -rf "$work"; }
trap cleanup EXIT
export CMP175_FIXTURE_PASSWORD=$(openssl rand -hex 20)
export MYSQL_ROOT_PASSWORD=$(openssl rand -hex 20)
container=$(docker_local run --pull=never -d --rm -p 127.0.0.1::3306 -e MYSQL_ROOT_PASSWORD \
    -e MYSQL_DATABASE=cmp175_ephemeral -e MYSQL_USER=cmp175_fixture -e MYSQL_PASSWORD="$CMP175_FIXTURE_PASSWORD" mysql:8.0.44)
unset MYSQL_ROOT_PASSWORD
sql() { docker_local exec -i -e MYSQL_PWD="$CMP175_FIXTURE_PASSWORD" "$container" mysql --protocol=TCP -h127.0.0.1 -ucmp175_fixture -N -B cmp175_ephemeral "$@"; }
for attempt in $(seq 1 90); do if sql -e 'SELECT 1' >/dev/null 2>&1; then break; fi; sleep 1; done
sql -e 'SELECT VERSION(), DATABASE()'
node --input-type=module > "$work/ddl.sql" <<'JS'
import fs from 'node:fs';
const dump=fs.readFileSync('db/yudao.sql','utf8');
const benefit=fs.readFileSync('sql/mysql/member_benefit.sql','utf8');
const imports=fs.readFileSync('sql/mysql/member_youzan_import.sql','utf8');
function table(text, name) {
    const lines=text.split('\n');
    const start=lines.findIndex(line=>new RegExp('^CREATE TABLE (?:IF NOT EXISTS )?`?'+name+'`?\\s*\\(').test(line));
    if (start<0) throw Error(name);
    const end=lines.findIndex((line,index)=>index>start && /^\)\s*ENGINE\s*=/i.test(line));
    const sql=lines.slice(start,end+1).join('\n');
    if(end<=start || !sql.trimEnd().endsWith(';') || /^(?:INSERT|UPDATE|DELETE|DROP|REPLACE|CREATE DATABASE)\b/im.test(sql)) throw Error(name);
    console.log(sql);
}
for(const name of ['member_user','member_level','member_point_record','member_level_record']) table(dump,name);
table(benefit,'member_point_batch');
for(const name of ['member_youzan_import_grant','member_youzan_import_batch','member_youzan_point_snapshot']) table(imports,name);
JS
sql < "$work/ddl.sql"
sql < sql/mysql/cmp158/02-member-recovery-floor.sql
sql -e "INSERT INTO member_youzan_point_snapshot(batch_id,merchant_id,mobile_hash,encrypted_mobile,snapshot_hash,restore_status,tenant_id) VALUES(1,9,'synthetic-history','fixture','fixture',1,1)"
if sql < sql/mysql/cmp172/01-youzan-claim-receipt.sql > "$work/negative.log" 2>&1; then echo 'Migration accepted unverifiable history' >&2; exit 1; fi
grep -q 'Historical non-pending snapshot' "$work/negative.log"
test "$(sql -e 'SELECT COUNT(*) FROM member_youzan_point_snapshot WHERE restore_status=1')" = 1
sql -e "DELETE FROM member_youzan_point_snapshot WHERE mobile_hash='synthetic-history'"
sql < sql/mysql/cmp172/01-youzan-claim-receipt.sql
test "$(sql -e 'SELECT @@transaction_isolation')" = REPEATABLE-READ
sql -e "INSERT INTO member_point_record(user_id,tenant_id,biz_type,biz_id,point,total_point,title) VALUES(11,1,3,'701',0,30,'fixture'),(12,1,3,'701',0,30,'fixture'),(13,2,3,'701',0,30,'fixture')"
if sql < sql/mysql/cmp172/02-youzan-recovery-source.sql > "$work/duplicate.log" 2>&1; then echo 'Migration accepted duplicate recovery history' >&2; exit 1; fi
grep -q 'ERROR 1062' "$work/duplicate.log"
test "$(sql -e 'SELECT COUNT(*) FROM member_point_record')" = 3
sql -e 'DELETE FROM member_point_record WHERE user_id=12'
sql < sql/mysql/cmp172/02-youzan-recovery-source.sql
sql < sql/mysql/V20261128_06__member_youzan_direct_claim.sql
sql -e "INSERT INTO member_youzan_point_snapshot(id,batch_id,merchant_id,mobile_hash,encrypted_mobile,snapshot_hash,restore_status,tenant_id) VALUES(701,1,9,'probe','probe','probe',0,1)"
docker_local exec -i "$container" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B cmp175_ephemeral' < scripts/cmp175/source-lock-probe.sql | tee "$work/locks.log"
awk -F '\t' '$3=="member_point_record" && ($5=="const" || $5=="ref" || $5=="eq_ref") && $7=="uk_youzan_recovery_source" {hit=1} END {exit !hit}' "$work/locks.log"
for verdict in CAPTURED_SOURCE RECORD_LOCKS_AFTER_HIT RECORD_LOCKS_AFTER_MISS; do
    expected=0; if [[ "$verdict" == CAPTURED_SOURCE ]]; then expected=1; fi
    grep -Fxq "${verdict}"$'\t'"$expected" "$work/locks.log"
done
export CMP175_MYSQL_CONTAINER_PORT=$(docker_local port "$container" 3306/tcp | sed -n 's/^127\.0\.0\.1://p')
[[ "$CMP175_MYSQL_CONTAINER_PORT" =~ ^[0-9]+$ ]]
mvn -B -ntp "$@" -pl yudao-module-member/yudao-module-member-server -am -Dflatten.skip=true \
  -Dtest=YouzanClaimFlowTest -Dsurefire.failIfNoSpecifiedTests=false \
  "-DargLine=-javaagent:$BYTE_BUDDY_AGENT -Djava.io.tmpdir=$work" test
node --input-type=module <<'JS'
import fs from 'node:fs';
const suite = fs.readFileSync('yudao-module-member/yudao-module-member-server/target/surefire-reports/TEST-cn.iocoder.yudao.module.member.service.youzan.YouzanClaimFlowTest.xml','utf8').match(/<testsuite\b[^>]*>/)?.[0] || '';
for (const [name, required] of Object.entries({tests:14, failures:0, errors:0, skipped:0})) {
    if (!suite.includes(` ${name}="${required}"`)) throw Error(`Unexpected native suite result: ${name}`);
}
console.log('Native flow: 14 passed, 0 skipped');
JS
