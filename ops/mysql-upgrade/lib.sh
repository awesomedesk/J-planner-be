#!/usr/bin/env bash
# 23-mysql-upgrade-plan.md 절차의 공통 함수. 실제 전환과 합성 검증이 같은 함수를 쓴다.
# 모든 함수는 실패하면 0이 아닌 값으로 돌아오고, 부르는 쪽은 set -euo pipefail.

# 컨테이너가 'ready for connections … port: 3306' (초기화용 임시 서버는 port 0)을 SINCE 이후 로그에 남길 때까지 기다린다.
# 제한시간(초, 기본 300) 초과·컨테이너 종료·[ERROR] 로그면 즉시 실패.
wait_up() {
  local c="$1" since="${2:-1970-01-01T00:00:00Z}" limit="${3:-300}" start=$SECONDS
  while :; do
    local state; state=$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null || echo missing)
    case "$state" in running|created|restarting) ;; *) echo "컨테이너가 실행 중이 아님($state): $c" >&2; return 3 ;; esac
    if docker logs --since "$since" "$c" 2>&1 | grep -q "\[ERROR\]"; then echo "[ERROR] 로그: $c" >&2; docker logs --since "$since" "$c" 2>&1 | grep "\[ERROR\]" | head -5 >&2; return 4; fi
    docker logs --since "$since" "$c" 2>&1 | grep -qE "ready for connections.*port: 3306 " && return 0
    if (( SECONDS - start >= limit )); then echo "ready 시간초과(${limit}초): $c" >&2; return 5; fi
    sleep 2
  done
}

# 접속 정보 파일로 ping까지
wait_ready() { wait_up "$@" && docker exec "$1" mysqladmin --defaults-extra-file=/root/.jp-upgrade.cnf ping >/dev/null; }

# 두 지문 파일 비교 — 허용하는 차이는 server.version 줄 하나뿐.
#   0 = 같음 ('지문 같음' 출력) / 1 = 내용이 다름 (다른 항목 이름만 출력) / 2 = 비교할 수 없음 (입력 없음·읽기 실패·구조 이상·필터·diff 오류)
# 1과 2 모두 실패다 — 부르는 쪽은 0일 때만 다음 단계로.
# 셋째 인자: (없음) 버전 줄만 뺌 / exact = 아무것도 빼지 않음(콜드 복원) / logical = 버전 줄 + mysql 계정 표 데이터 줄을 뺌(논리 복원 — root가 새로 생김, 계정은 SHOW GRANTS로 따로)
compare_fp() {
  local a="$1" b="$2" mode="${3:-}" f
  local -a ex=(-e '^#')
  case "$mode" in
    '') ex+=(-e '^server\.version') ;;
    exact) ;;
    logical) ex+=(-e '^server\.version' -e '^data\.mysql\.') ;;
    *) echo "비교할 수 없음: 모르는 방식 $mode" >&2; return 2 ;;
  esac
  for f in "$a" "$b"; do
    if [ ! -f "$f" ] || [ ! -r "$f" ] || [ ! -s "$f" ]; then echo "비교할 수 없음: 입력 없음·읽기 불가·빈 파일 — $f" >&2; return 2; fi
    for key in '^server\.version' '^server\.settings' '^schema\.tables' '^schema\.columns' '^data\.' '^warnings\.'; do
      grep -qE "$key" "$f" || { echo "비교할 수 없음: 지문 구조가 아님($key 없음) — $f" >&2; return 2; }
    done
  done
  local tmp; tmp=$(mktemp -d) || return 2
  local rc=0
  # grep -v: 0 = 남은 줄 있음, 1 = 남은 줄 없음(구조 확인으로 생기지 않음), 2 = 오류 → 0만 통과
  grep -v "${ex[@]}" "$a" > "$tmp/a" || rc=$?
  [ $rc -eq 0 ] && { grep -v "${ex[@]}" "$b" > "$tmp/b" || rc=$?; }
  if [ $rc -ne 0 ]; then echo "비교할 수 없음: 필터 실패(grep 종료 $rc)" >&2; rm -rf "$tmp"; return 2; fi
  diff "$tmp/a" "$tmp/b" > "$tmp/diff" || rc=$?
  case $rc in
    0) echo "지문 같음 (${mode:-버전 줄 제외} $(grep -c . "$tmp/a")줄)"; rm -rf "$tmp"; return 0 ;;
    1) echo "지문 다름 — 항목:" >&2; grep -oE '^[<>] [^	]+' "$tmp/diff" | sort -u >&2; rm -rf "$tmp"; return 1 ;;
    *) echo "비교할 수 없음: diff 오류(종료 $rc)" >&2; rm -rf "$tmp"; return 2 ;;
  esac
}

# 계정·권한 백업 — 컨테이너 안에서 bash로, 명령마다 종료 코드 확인. 성공해야만 ACCOUNTS_OK
# JP_FAIL_INJECT=list|create|grants 는 합성 검증용 실패 주입
export_accounts() {
  local c="$1" inject="${2:-}"
  docker exec -i -e JP_FAIL_INJECT="$inject" "$c" bash -s <<'IN'
set -euo pipefail; umask 077
C=(mysql --defaults-extra-file=/root/.jp-upgrade.cnf -N -B)
F=/tmp/accounts.sql; T=/tmp/accounts.sql.part
[ ! -e "$F" ] && [ ! -e "$T" ] || { echo "이미 있음 — 멈춤" >&2; exit 1; }
trap 'rm -f "$T"' ERR
LIST_SQL="SELECT CONCAT(QUOTE(user),'@',QUOTE(host)) FROM mysql.user WHERE user NOT IN ('root','mysql.infoschema','mysql.session','mysql.sys') ORDER BY user, host"
[ "${JP_FAIL_INJECT:-}" = list ] && LIST_SQL="SELECT no_such_column FROM mysql.user"
ACCOUNTS=$("${C[@]}" -e "$LIST_SQL")
: > "$T"
while IFS= read -r A; do
  [ -n "$A" ] || continue
  WHO="$A"; [ "${JP_FAIL_INJECT:-}" = create ] && WHO="'no_such_user'@'%'"
  CU=$("${C[@]}" -e "SET SESSION print_identified_with_as_hex = ON; SHOW CREATE USER $WHO")
  G_SQL="SHOW GRANTS FOR $A"; [ "${JP_FAIL_INJECT:-}" = grants ] && G_SQL="SHOW GRANTZ FOR $A"
  GR=$("${C[@]}" -e "$G_SQL")
  printf '%s;\n' "$CU" >> "$T"; printf '%s\n' "$GR" | sed 's/$/;/' >> "$T"
done <<< "$ACCOUNTS"
N=$(printf '%s\n' "$ACCOUNTS" | grep -c . || true)
[ "$(grep -c '^CREATE USER' "$T")" = "$N" ] || { echo "계정 수가 맞지 않음 — 멈춤" >&2; rm -f "$T"; exit 1; }
mv "$T" "$F"; echo "ACCOUNTS_OK $N"
IN
}
