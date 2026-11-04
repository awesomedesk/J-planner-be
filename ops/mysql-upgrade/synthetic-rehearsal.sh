#!/usr/bin/env bash
# 23-mysql-upgrade-plan.md 절차를 일회성 '합성' MySQL로 끝까지 돌려 본다 (실데이터·실비밀번호 없음).
# 공유 mysql_db는 스키마 정의(--no-data)만 읽고 바꾸지 않는다. 합성 컨테이너·폴더는 끝나면 지운다(합성이라 승인 대상 아님).
#   사용: ./synthetic-rehearsal.sh [옛 이미지 mysql:8.4.6] [새 이미지 mysql:8.4.13]
set -euo pipefail
OLD_IMG="${1:-mysql:8.4.6}"; NEW_IMG="${2:-mysql:8.4.13}"
HERE="$(cd "$(dirname "$0")" && pwd)"; umask 077
RUN="$(mktemp -d "${TMPDIR:-/tmp}/jp-synth-upgrade.XXXXXX")"; chmod 700 "$RUN"
OLD=jp_synth_old_$$; NEW=jp_synth_new_$$; RESTORE=jp_synth_restore_$$
for n in $OLD $NEW $RESTORE; do docker ps -a --format '{{.Names}}' | grep -qx "$n" && { echo "컨테이너 이름 충돌: $n — 멈춤"; exit 1; }; done
PW="$(openssl rand -hex 24)"   # 합성 전용, 메모리에만
cleanup() { docker rm -f $OLD $NEW $RESTORE >/dev/null 2>&1 || true; rm -rf "$RUN"; }
trap cleanup EXIT
step() { echo "== $*"; }
# 'ready for connections … port: 3306 ' = 최종 서버 (첫 기동의 초기화용 임시 서버는 port: 0). 그 뒤 접속 정보로 ping
wait_up() { local since="${2:-1970-01-01T00:00:00Z}"; for i in $(seq 1 150); do docker logs --since "$since" "$1" 2>&1 | grep -qE "ready for connections.*port: 3306 " && return 0; sleep 2; done; echo "준비 안 됨: $1"; docker logs "$1" 2>&1 | tail -20; exit 1; }
wait_ready() { wait_up "$1" "${2:-}"; docker exec "$1" mysqladmin --defaults-extra-file=/root/.jp-upgrade.cnf ping >/dev/null || { echo "접속 실패: $1"; exit 1; }; }
# 대화형 접속 정보 만들기 — 23 문서 4-0의 명령을 expect로 그대로 실행 (비밀번호는 화면·인자·기록에 안 남음)
make_cnf() { expect -c "
  log_user 0; set timeout 30
  spawn docker exec -it $1 sh -c {umask 077; printf 'root 비밀번호: '; stty -echo; read P; stty echo; printf '\n[client]\nuser=root\npassword=%s\n' \"\$P\" > /root/.jp-upgrade.cnf; unset P; echo CNF_OK}
  expect \"비밀번호: \"; send -- \"\$env(PW)\r\"; expect CNF_OK; expect eof" ; }
export PW

step "1. 옛 버전($OLD_IMG) 합성 서버 + 실제 jp·jpadm 스키마 정의(데이터 없음) + 합성 데이터·계정"
mkdir -m 700 "$RUN/datadir"
docker run -d --name $OLD -e TZ=Asia/Seoul -e MYSQL_ROOT_PASSWORD="$PW" -v "$RUN/datadir":/var/lib/mysql "$OLD_IMG" --lower_case_table_names=1 >/dev/null
wait_up $OLD; make_cnf $OLD; wait_ready $OLD
docker exec mysql_db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --no-data --skip-comments --databases jp jpadm 2>/dev/null' > "$RUN/schema.sql"
docker exec -i $OLD mysql --defaults-extra-file=/root/.jp-upgrade.cnf < "$RUN/schema.sql"
docker exec -i $OLD mysql --defaults-extra-file=/root/.jp-upgrade.cnf jp <<'SQL'
INSERT INTO users (user_id, email, name, status) VALUES (1, 'qa-synth-1@example.com', '합성1', 'ACTIVE'), (2, NULL, NULL, 'DORMANT');
INSERT INTO diaries (user_id, diary_date, content_enc) VALUES (1, '2026-01-01', RANDOM_BYTES(64)), (1, '2026-01-02', RANDOM_BYTES(90));
INSERT INTO memos (user_id, title_enc, content_enc) VALUES (1, RANDOM_BYTES(40), NULL), (1, NULL, RANDOM_BYTES(70));
INSERT INTO audit_logs (user_id, actor, event, created_at) VALUES (1, 'USER', 'LOGIN', NOW()), (NULL, 'SYSTEM', 'DORMANT', NOW());
CREATE USER 'qa_synth_app'@'%' IDENTIFIED BY 'synthetic-only-Pw1!'; GRANT SELECT, INSERT ON jp.* TO 'qa_synth_app'@'%';
CREATE USER 'qa_synth_col'@'%' IDENTIFIED BY 'synthetic-only-Pw2!'; GRANT SELECT (user_id, status) ON jp.users TO 'qa_synth_col'@'%';
SQL

step "2. 동결 상태 지문(before)"
"$HERE/fingerprint.sh" $OLD "$RUN/before.txt"

step "3. 최종 논리 백업 — 실패 코드 전파·기존 파일 거부·'Dump completed'·크기 게이트"
docker exec $OLD sh -c 'set -e; umask 077; F=/tmp/final.sql; [ ! -e $F ] || { echo "이미 있음"; exit 1; }; mysqldump --defaults-extra-file=/root/.jp-upgrade.cnf --single-transaction --routines --triggers --events --set-gtid-purged=OFF --databases jp jpadm --result-file=$F; tail -c 40 $F | grep -q "Dump completed"; test -s $F; echo DUMP_OK'
docker cp $OLD:/tmp/final.sql "$RUN/final.sql" >/dev/null; chmod 600 "$RUN/final.sql"; docker exec $OLD rm -f /tmp/final.sql
step "3b. 계정·권한 백업 — SHOW CREATE USER + SHOW GRANTS를 컨테이너 안 보호 파일로 (화면 출력 없음)"
docker exec $OLD sh -c 'set -e; umask 077; C="mysql --defaults-extra-file=/root/.jp-upgrade.cnf -N -B"; F=/tmp/accounts.sql; [ ! -e $F ] || exit 1
  $C -e "SELECT CONCAT(QUOTE(user),\"@\",QUOTE(host)) FROM mysql.user WHERE user NOT IN (\"root\",\"mysql.infoschema\",\"mysql.session\",\"mysql.sys\")" | while read -r A; do
    $C -e "SET SESSION print_identified_with_as_hex = ON; SHOW CREATE USER $A" | sed "s/\$/;/"; $C -e "SHOW GRANTS FOR $A" | sed "s/\$/;/"; done > $F; test -s $F; echo ACCOUNTS_OK $(grep -c "^CREATE USER" $F)'
docker cp $OLD:/tmp/accounts.sql "$RUN/accounts.sql" >/dev/null; chmod 600 "$RUN/accounts.sql"; docker exec $OLD rm -f /tmp/accounts.sql
step "3c. 콜드 백업 — 멈추고 폴더 통째 복사, 파일 수 비교"
docker stop $OLD >/dev/null
cp -a "$RUN/datadir" "$RUN/final-datadir"
[ "$(find "$RUN/datadir" | wc -l)" = "$(find "$RUN/final-datadir" | wc -l)" ] && echo COLD_OK

step "4. 전환 — 같은 폴더로 새 버전($NEW_IMG), 초기화 비밀번호 없이"
docker run -d --name $NEW -e TZ=Asia/Seoul -v "$RUN/datadir":/var/lib/mysql "$NEW_IMG" --lower_case_table_names=1 >/dev/null
wait_up $NEW; make_cnf $NEW; wait_ready $NEW   # 접속 정보는 새 컨테이너에서 다시 대화형으로 (옛 컨테이너를 켜지 않음 — 같은 폴더를 두 서버가 쓰면 안 됨)
docker logs $NEW 2>&1 | grep -E "\[ERROR\]" && { echo "새 서버 오류 로그 — 멈춤"; exit 1; } || echo "새 서버 ERROR 없음: $(docker exec $NEW mysql --defaults-extra-file=/root/.jp-upgrade.cnf -N -e 'SELECT VERSION()')"
"$HERE/fingerprint.sh" $NEW "$RUN/after.txt"
if diff <(grep -v -e '^#' -e '^server' "$RUN/before.txt") <(grep -v -e '^#' -e '^server' "$RUN/after.txt") > "$RUN/diff.txt"; then echo "UPGRADE_SAME (서버 줄 빼고 지문 모두 같음)"; else echo "다름: $(wc -l < "$RUN/diff.txt")줄 — 항목:"; grep -oE '^[<>] [^	]+' "$RUN/diff.txt" | sort -u; fi

step "5. 실패 가정 — 콜드 백업으로 옛 버전 복원"
docker rm -f $NEW >/dev/null
mv "$RUN/datadir" "$RUN/failed-datadir"; cp -a "$RUN/final-datadir" "$RUN/datadir"
T=$(date -u +%Y-%m-%dT%H:%M:%SZ); docker start $OLD >/dev/null; wait_ready $OLD "$T"
"$HERE/fingerprint.sh" $OLD "$RUN/restored.txt"
diff <(grep -v '^#' "$RUN/before.txt") <(grep -v '^#' "$RUN/restored.txt") >/dev/null && echo "COLD_RESTORE_SAME"

step "6. 콜드 백업이 깨졌다고 가정 — 빈 옛 버전 서버에 논리 백업 + 계정 파일 복원"
mkdir -m 700 "$RUN/restore-datadir"
docker run -d --name $RESTORE -e TZ=Asia/Seoul -e MYSQL_ROOT_PASSWORD="$PW" -v "$RUN/restore-datadir":/var/lib/mysql "$OLD_IMG" --lower_case_table_names=1 >/dev/null
wait_up $RESTORE; make_cnf $RESTORE; wait_ready $RESTORE
# 오류 메시지에 비밀번호 해시가 섞일 수 있어 화면에 내지 않고 종료 코드만 본다
docker exec -i $RESTORE mysql --defaults-extra-file=/root/.jp-upgrade.cnf < "$RUN/final.sql" >/dev/null 2>&1 || { echo "논리 백업 복원 실패 — 멈춤"; exit 1; }
docker exec -i $RESTORE mysql --defaults-extra-file=/root/.jp-upgrade.cnf < "$RUN/accounts.sql" >/dev/null 2>&1 || { echo "계정 복원 실패 — 멈춤"; exit 1; }
"$HERE/fingerprint.sh" $RESTORE "$RUN/logical.txt"
if diff <(grep -v -e '^#' -e '^server' -e '^data\.mysql\.' -e '^warnings' "$RUN/before.txt") <(grep -v -e '^#' -e '^server' -e '^data\.mysql\.' -e '^warnings' "$RUN/logical.txt") >/dev/null; then echo "LOGICAL_RESTORE_SAME (jp·jpadm 스키마·데이터)"; else echo "논리 복원 지문 다름"; fi
docker exec $RESTORE mysql --defaults-extra-file=/root/.jp-upgrade.cnf -N -e "SELECT COUNT(*) FROM mysql.user WHERE user LIKE 'qa\\_synth\\_%'; SHOW GRANTS FOR 'qa_synth_col'@'%';" | sed 's/^/  계정 복원: /'
echo "== 끝 (합성 컨테이너·폴더 정리)"
