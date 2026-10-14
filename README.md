# J-planner

## 📄프로젝트 소개

MBTI 유형 중 'J' 유형을 갖고 있는 사람들이 사용하고 싶은 플래너를 만드는 것이 목표이다.

캘린더, 플래너, To-Do, 일기장 등의 기능을 보유한다.

[J-planner]를 사용한다면, 다양한 기기로 당신의 과거, 현재, 미래를 관리할 수 있다. 


---

## 🛠 로컬 실행

### 준비물
- Java 25
- MySQL 8.4 — `127.0.0.1:3306`, 서비스 DB `jp` / 테스트 DB `jp_test`·`jp_test_migration`(자동 생성)
  - `jplanner` 계정에 두 테스트 DB 권한이 있어야 한다. 처음 한 번 root로:
    ```sql
    GRANT ALL PRIVILEGES ON jp_test.* TO 'jplanner'@'%';
    GRANT ALL PRIVILEGES ON jp_test_migration.* TO 'jplanner'@'%';
    ```
    (`jp_test_migration`은 US-32 마이그레이션 테스트가 V1부터 다시 쌓아 보는 빈 DB)
- Redis 8.2 — `127.0.0.1:6379`, **Docker로 켠다** (아래)
- 프로젝트 루트의 `.env` (git에 올리지 않음): `JP_DB_PASSWORD`, `JP_TEST_DB_PASSWORD`, `JP_ADMIN_EMAIL`, `JP_DEV_ACCOUNTS` 등 — 자세한 것은 `CLAUDE.md`의 Local DB
- 기획 저장소 `j-planner-product`가 이 저장소 옆에 있어야 빌드된다 (`08-openapi.yaml`)

### Redis (Docker)
로그인 세션·요청 제한(US-34)에 쓴다.

```bash
# brew로 설치한 Redis가 켜져 있으면 6379가 겹치니 먼저 끈다
brew services stop redis

# 켜기 (처음 한 번. 이후에는 Docker가 켜질 때 같이 켜진다)
docker run -d --name jp-redis --restart unless-stopped \
  -p 127.0.0.1:6379:6379 \
  redis:8.2-alpine --save "" --appendonly no

# 확인 → PONG
docker exec jp-redis redis-cli ping
```

| 옵션 | 이유 |
|---|---|
| `redis:8.2-alpine` | 8.x 중 유일한 Extended 릴리스(LTS 격, 보안 지원 2030-09-01까지). 8.10 같은 Standard 릴리스는 다음 버전이 나오고 6개월이면 지원이 끝난다. 운영도 8.2로 맞춘다. 코드는 `INCR`·`EXPIRE`·`SET` 같은 기본 명령만 써서 8.2로 충분하다 (8.8의 `INCREX`는 안 씀). 출처: [Redis 버전 관리](https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/) |
| `-p 127.0.0.1:6379:6379` | 내 컴퓨터에서만 접속. `-p 6379:6379`로 열면 같은 네트워크의 다른 기기가 비밀번호 없는 Redis에 붙을 수 있다 |
| `--save "" --appendonly no` | 디스크에 저장하지 않는다. 로컬은 껐다 켜면 다시 로그인하면 된다 |
| `--restart unless-stopped` | Docker가 켜질 때 같이 켜진다 (직접 `docker stop jp-redis` 하면 그대로 꺼져 있음) |

- 앱 설정은 바꿀 것 없다 (local 프로필은 `127.0.0.1:6379`).
- 서버는 Redis **0번 DB**, 테스트는 **1번 DB**를 쓴다. 테스트가 매번 1번 DB만 비우므로 서버를 켜 둔 채 테스트를 돌려도 로그인이 풀리지 않는다.
- 끄기 `docker stop jp-redis` · 다시 켜기 `docker start jp-redis` · 지우기 `docker rm -f jp-redis`

### 서버·테스트
```bash
./gradlew bootRun   # http://localhost:8080 (Swagger UI: /swagger-ui.html)
./gradlew test      # MySQL·Redis가 켜져 있어야 한다
```
