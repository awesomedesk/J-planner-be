#!/usr/bin/env bash
# 실패 주입 합성 검증 — 23 문서의 게이트가 '실패할 때 실제로 멈추는지' 본다 (PO #349 3차).
# 각 경우가 기대대로 실패(0이 아닌 종료·성공 표식 없음)해야 PASS. 실데이터·실비밀번호 없음, 합성 컨테이너는 끝나고 지움.
set -euo pipefail
IMG="${1:-mysql:8.4.6}"
HERE="$(cd "$(dirname "$0")" && pwd)"; umask 077; source "$HERE/lib.sh"
RUN="$(mktemp -d "${TMPDIR:-/tmp}/jp-synth-fail.XXXXXX")"; chmod 700 "$RUN"
S=jp_synth_fail_$$; BAD=jp_synth_bad_$$
PW="$(openssl rand -hex 24)"; export PW
cleanup() { docker rm -f $S $BAD >/dev/null 2>&1 || true; rm -rf "$RUN"; }; trap cleanup EXIT
pass=0; failn=0
expect_fail() { # 이름, 명령… — 명령이 실패하고 출력에 성공 표식(…_OK·같음)이 없어야 PASS
  local name="$1"; shift; local out rc=0
  out=$("$@" 2>&1) || rc=$?
  if [ $rc -ne 0 ] && ! grep -qE '_OK|지문 같음' <<<"$out"; then echo "PASS  $name (종료 $rc)"; pass=$((pass+1)); else echo "FAIL  $name (종료 $rc, 성공 표식이 나오거나 멈추지 않음)"; failn=$((failn+1)); fi
}
expect_ok() { local name="$1"; shift; if "$@" >/dev/null 2>&1; then echo "PASS  $name"; pass=$((pass+1)); else echo "FAIL  $name"; failn=$((failn+1)); fi; }
make_cnf() { expect -c "
  log_user 0; set timeout 30
  spawn docker exec -it $1 sh -c {umask 077; printf 'pw: '; stty -echo; read P; stty echo; printf '\n[client]\nuser=root\npassword=%s\n' \"\$P\" > /root/.jp-upgrade.cnf; unset P; echo CNF_OK}
  expect \"pw: \"; send -- \"\$env(PW)\r\"; expect CNF_OK; expect eof"; }

echo "== 준비: 합성 서버($IMG) + 합성 스키마·데이터·계정"
docker run -d --name $S -e TZ=Asia/Seoul -e MYSQL_ROOT_PASSWORD="$PW" "$IMG" --lower_case_table_names=1 >/dev/null
wait_up $S; make_cnf $S; wait_ready $S
docker exec -i $S mysql --defaults-extra-file=/root/.jp-upgrade.cnf <<'SQL'
CREATE DATABASE jp; CREATE DATABASE jpadm;
CREATE TABLE jp.t (id INT PRIMARY KEY, v VARBINARY(64), w VARCHAR(20) NULL, CONSTRAINT ck_w CHECK (w <> 'x'));
INSERT INTO jp.t VALUES (1, RANDOM_BYTES(32), NULL), (2, RANDOM_BYTES(32), 'a'), (3, RANDOM_BYTES(32), 'b');
INSERT INTO jp.t WITH RECURSIVE n(i) AS (SELECT 4 UNION ALL SELECT i + 1 FROM n WHERE i < 200) SELECT i, RANDOM_BYTES(16), NULL FROM n;
CREATE TABLE jpadm.a (id INT PRIMARY KEY); INSERT INTO jpadm.a VALUES (1);
CREATE USER 'qa_synth_app'@'%' IDENTIFIED BY 'synthetic-only-Pw1!'; GRANT SELECT ON jp.* TO 'qa_synth_app'@'%';
SQL
"$HERE/fingerprint.sh" $S "$RUN/base.txt" >/dev/null

echo "== 1. 계정 백업 오류 전파 (컨테이너 안 bash pipefail + 명령마다 종료 코드)"
for inj in list create grants; do expect_fail "계정 백업 실패 주입: $inj" export_accounts $S $inj; docker exec $S rm -f /tmp/accounts.sql /tmp/accounts.sql.part; done
expect_ok "계정 백업 정상 경로" export_accounts $S; docker exec $S rm -f /tmp/accounts.sql

echo "== 2. 설정 비교 (버전 줄만 허용)"
"$HERE/fingerprint.sh" $S "$RUN/same.txt" >/dev/null
expect_ok "같은 서버 두 번 = 같음" compare_fp "$RUN/base.txt" "$RUN/same.txt"
docker exec $S mysql --defaults-extra-file=/root/.jp-upgrade.cnf -e "SET PERSIST sql_mode = 'STRICT_TRANS_TABLES'"
"$HERE/fingerprint.sh" $S "$RUN/setting.txt" >/dev/null
expect_fail "설정(sql_mode) 하나만 바뀜 → 비교 실패" compare_fp "$RUN/base.txt" "$RUN/setting.txt"
docker exec $S mysql --defaults-extra-file=/root/.jp-upgrade.cnf -e "RESET PERSIST sql_mode; SET GLOBAL sql_mode = DEFAULT"
sed 's/^server\.version.*/server.version\t9.9.9/' "$RUN/base.txt" > "$RUN/version-only.txt"
expect_ok "버전 줄만 다름 → 통과" compare_fp "$RUN/base.txt" "$RUN/version-only.txt"
docker exec $S mysql --defaults-extra-file=/root/.jp-upgrade.cnf -e "UPDATE jp.t SET v = RANDOM_BYTES(32) WHERE id = 2"
"$HERE/fingerprint.sh" $S "$RUN/data.txt" >/dev/null
expect_fail "데이터 한 행 바뀜 → 비교 실패" compare_fp "$RUN/base.txt" "$RUN/data.txt"

echo "== 3. 지문 경고 (GROUP_CONCAT 상한을 작게 → 잘림)"
expect_fail "group_concat_max_len=8 → 지문 실패" env FP_GC_MAX=8 "$HERE/fingerprint.sh" $S "$RUN/trunc.txt"
expect_fail "group_concat_max_len=200 → 지문 실패" env FP_GC_MAX=200 "$HERE/fingerprint.sh" $S "$RUN/trunc2.txt"
# 생성 SQL은 멀쩡하고 데이터 집계(200행 × 32자)만 잘리는 상한 → 경고 게이트로 종료 2여야 함
expect_ok "데이터 집계만 잘림(상한 4000) → 경고 게이트 종료 2" bash -c 'FP_GC_MAX=4000 "$0" "$1" "$2" >/dev/null 2>&1; test $? -eq 2' "$HERE/fingerprint.sh" $S "$RUN/trunc3.txt"

echo "== 4. ready 대기 실패"
docker run -d --name $BAD "$IMG" --no-such-option-for-test >/dev/null 2>&1 || true
expect_fail "잘못된 옵션으로 종료/ERROR → 대기 즉시 실패" wait_up $BAD "1970-01-01T00:00:00Z" 60
docker rm -f $BAD >/dev/null 2>&1 || true
docker run -d --name $BAD --entrypoint sleep "$IMG" 600 >/dev/null
expect_fail "ready 줄이 안 나옴 → 시간초과(6초)" wait_up $BAD "1970-01-01T00:00:00Z" 6

echo "== 5. 덤프 게이트 (기존 파일 거부)"
docker exec $S sh -c 'echo partial > /tmp/final.sql'
expect_fail "기존 덤프 파일이 있으면 멈춤" docker exec $S bash -c 'set -euo pipefail; umask 077; F=/tmp/final.sql; [ ! -e $F ] || { echo "이미 있음"; exit 1; }; echo DUMP_OK'
docker exec $S rm -f /tmp/final.sql
expect_fail "덤프 명령 실패(없는 스키마) → DUMP_OK 안 나옴" docker exec $S bash -c 'set -euo pipefail; umask 077; F=/tmp/final.sql; mysqldump --defaults-extra-file=/root/.jp-upgrade.cnf --single-transaction --databases no_such_schema --result-file=$F; tail -c 40 $F | grep -q "Dump completed"; test -s $F; echo DUMP_OK'

echo "== 결과: PASS $pass · FAIL $failn"
[ $failn -eq 0 ]
