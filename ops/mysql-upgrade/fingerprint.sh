#!/usr/bin/env bash
# MySQL 업그레이드 전후 비교용 지문 (j-planner-product 23-mysql-upgrade-plan.md 4-2).
# 값·내용·비밀번호 해시는 출력하지 않고 개수와 MD5 지문만 OUT 파일(600)에 쓴다.
#   사용: ./fingerprint.sh <컨테이너> <OUT 파일> [스키마들, 기본 "jp jpadm"]
#   전제: 컨테이너 안 /root/.jp-upgrade.cnf (root 접속 정보, 600) — 23 문서 4-0의 대화형 방법으로 만든다.
set -euo pipefail
C="$1"; OUT="$2"; shift 2; SCHEMAS="${*:-jp jpadm}"
[ -e "$OUT" ] && { echo "이미 있음: $OUT — 멈춤" >&2; exit 1; }
umask 077
IN=$(printf "'%s'," $SCHEMAS); IN="${IN%,}"
SQL() { docker exec -i "$C" mysql --defaults-extra-file=/root/.jp-upgrade.cnf -N -B; }
PRE="SET SESSION group_concat_max_len = 1073741824;"
{
  echo "# 지문 $(date '+%F %T %Z') 컨테이너=$C 스키마=$SCHEMAS"
  echo "$PRE SELECT 'server', VERSION(), @@lower_case_table_names, @@character_set_server, @@collation_server, @@sql_mode, @@time_zone, @@system_time_zone, @@default_storage_engine;" | SQL
  # 스키마 정의: 테이블·열·인덱스·제약·FK·CHECK·뷰·루틴·트리거·이벤트
  cat <<SQLX | SQL
$PRE
SELECT 'schema.tables', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, table_schema, table_name, table_type, IFNULL(engine,'~'), IFNULL(row_format,'~'), IFNULL(table_collation,'~'), IFNULL(create_options,'~')) ORDER BY table_schema, table_name SEPARATOR 0x1e)) FROM information_schema.tables WHERE table_schema IN ($IN);
SELECT 'schema.columns', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, table_schema, table_name, ordinal_position, column_name, column_type, is_nullable, IFNULL(column_default,'~'), IFNULL(character_set_name,'~'), IFNULL(collation_name,'~'), extra, IFNULL(generation_expression,'~'), column_key) ORDER BY table_schema, table_name, ordinal_position SEPARATOR 0x1e)) FROM information_schema.columns WHERE table_schema IN ($IN);
SELECT 'schema.indexes', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, table_schema, table_name, index_name, non_unique, seq_in_index, IFNULL(column_name,'~'), IFNULL(sub_part,'~'), IFNULL(expression,'~'), index_type, is_visible) ORDER BY table_schema, table_name, index_name, seq_in_index SEPARATOR 0x1e)) FROM information_schema.statistics WHERE table_schema IN ($IN);
SELECT 'schema.constraints', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, k.constraint_schema, k.table_name, k.constraint_name, k.column_name, k.ordinal_position, IFNULL(k.referenced_table_name,'~'), IFNULL(k.referenced_column_name,'~'), IFNULL(r.update_rule,'~'), IFNULL(r.delete_rule,'~')) ORDER BY k.constraint_schema, k.table_name, k.constraint_name, k.ordinal_position SEPARATOR 0x1e)) FROM information_schema.key_column_usage k LEFT JOIN information_schema.referential_constraints r ON r.constraint_schema = k.constraint_schema AND r.constraint_name = k.constraint_name AND r.table_name = k.table_name WHERE k.constraint_schema IN ($IN);
SELECT 'schema.table_constraints', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, constraint_schema, table_name, constraint_name, constraint_type, enforced) ORDER BY constraint_schema, table_name, constraint_name SEPARATOR 0x1e)) FROM information_schema.table_constraints WHERE constraint_schema IN ($IN);
SELECT 'schema.checks', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, constraint_schema, constraint_name, check_clause) ORDER BY constraint_schema, constraint_name SEPARATOR 0x1e)) FROM information_schema.check_constraints WHERE constraint_schema IN ($IN);
SELECT 'schema.views', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, table_schema, table_name, view_definition, check_option, security_type) ORDER BY table_schema, table_name SEPARATOR 0x1e)) FROM information_schema.views WHERE table_schema IN ($IN);
SELECT 'schema.routines', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, routine_schema, routine_name, routine_type, IFNULL(routine_definition,'~'), security_type) ORDER BY routine_schema, routine_name SEPARATOR 0x1e)) FROM information_schema.routines WHERE routine_schema IN ($IN);
SELECT 'schema.triggers', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, trigger_schema, trigger_name, action_statement) ORDER BY trigger_schema, trigger_name SEPARATOR 0x1e)) FROM information_schema.triggers WHERE trigger_schema IN ($IN);
SELECT 'schema.events', COUNT(*), MD5(GROUP_CONCAT(CONCAT_WS(0x1f, event_schema, event_name, event_definition) ORDER BY event_schema, event_name SEPARATOR 0x1e)) FROM information_schema.events WHERE event_schema IN ($IN);
SELECT 'warnings', @@warning_count;
SQLX
  # 데이터: 표마다 행 수 + 모든 열(HEX, NULL은 따로 표시) 행별 MD5를 정렬해 묶은 MD5. 내용은 나오지 않는다
  GEN="$PRE SELECT CONCAT('SELECT ''data.', t.table_schema, '.', t.table_name, ''', COUNT(*), IFNULL(MD5(GROUP_CONCAT(h ORDER BY h SEPARATOR '''')), ''empty'') FROM (SELECT MD5(CONCAT_WS(0x1f, ', GROUP_CONCAT(CONCAT('IFNULL(HEX(\`', c.column_name, '\`), ''<null>'')') ORDER BY c.ordinal_position SEPARATOR ', '), ')) AS h FROM \`', t.table_schema, '\`.\`', t.table_name, '\`) x; SELECT ''warnings.', t.table_name, ''', @@warning_count;') FROM information_schema.tables t JOIN information_schema.columns c ON c.table_schema = t.table_schema AND c.table_name = t.table_name WHERE t.table_type = 'BASE TABLE' AND t.table_schema IN ($IN, 'mysql') AND (t.table_schema <> 'mysql' OR t.table_name IN ('user','db','tables_priv','columns_priv','procs_priv','proxies_priv','role_edges','default_roles','global_grants')) GROUP BY t.table_schema, t.table_name ORDER BY t.table_schema, t.table_name;"
  { echo "$PRE"; echo "$GEN" | SQL; } | SQL
} > "$OUT"
chmod 600 "$OUT"
if grep -E "^warnings" "$OUT" | awk '{ if ($NF != 0) bad=1 } END { exit bad }'; then
  echo "지문 $(grep -c . "$OUT")줄 → $OUT (경고 0)"
else
  echo "경고가 있음 (GROUP_CONCAT 잘림 등) — 지문을 믿지 말고 멈춤: $OUT" >&2; exit 2
fi
