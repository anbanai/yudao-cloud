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
container=$(docker_local run --pull=never -d --rm -p 127.0.0.1::3306 -e MYSQL_RANDOM_ROOT_PASSWORD=yes \
    -e MYSQL_DATABASE=cmp175_ephemeral -e MYSQL_USER=cmp175_fixture -e MYSQL_PASSWORD="$CMP175_FIXTURE_PASSWORD" mysql:8.0.44)
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
export CMP175_MYSQL_CONTAINER_PORT=$(docker_local port "$container" 3306/tcp | sed -n 's/^127\.0\.0\.1://p')
[[ "$CMP175_MYSQL_CONTAINER_PORT" =~ ^[0-9]+$ ]]
mvn -B -ntp "$@" -pl yudao-module-member/yudao-module-member-server -am -Dflatten.skip=true \
  -Dtest=YouzanClaimFlowTest -Dsurefire.failIfNoSpecifiedTests=false \
  "-DargLine=-javaagent:$BYTE_BUDDY_AGENT -Djava.io.tmpdir=$work" test
