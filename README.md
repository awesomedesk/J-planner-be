# J-planner

## 📄프로젝트 소개

MBTI 유형 중 'J' 유형을 갖고 있는 사람들이 사용하고 싶은 플래너를 만드는 것이 목표이다.

캘린더, 플래너, To-Do, 일기장 등의 기능을 보유한다.

[J-planner]를 사용한다면, 다양한 기기로 당신의 과거, 현재, 미래를 관리할 수 있다. 


---

## 🛠 로컬 실행

### 준비물
- Java 25
- MySQL 8.4 — `127.0.0.1:3306`, 서비스 DB `jp` / 테스트 DB `jp_test`·`jp_test_migration`(자동 생성). **Docker로 켠다** (아래)
- Redis 8.2 — `127.0.0.1:6379`, **Docker로 켠다** (아래)
- 프로젝트 루트의 `.env` (git에 올리지 않음): `JP_DB_PASSWORD`, `JP_TEST_DB_PASSWORD`, `JP_ADMIN_EMAIL`, `JP_DEV_ACCOUNTS` 등 — 자세한 것은 `CLAUDE.md`의 Local DB
- 기획 저장소 `j-planner-product`가 이 저장소 옆에 있어야 빌드된다 (`08-openapi.yaml`)

### MySQL (Docker)
이미 켜 둔 MySQL 컨테이너가 있으면 그대로 쓴다. 이름은 `docker ps`로 찾는다 (아래 `<MySQL 컨테이너>` 자리).

```bash
# 컨테이너 이름 찾기 (IMAGE가 mysql인 줄의 NAMES)
docker ps

# MySQL에 접속 (root 또는 jplanner)
docker exec -it <MySQL 컨테이너> mysql -u root -p
```

처음 만든다면 (예시 — 비밀번호는 직접 정해서 `.env`에만 적는다):
```bash
docker run -d --name jp-mysql --restart unless-stopped \
  -p 127.0.0.1:3306:3306 \
  -e MYSQL_ROOT_PASSWORD='<root 비밀번호>' \
  -v jp-mysql-data:/var/lib/mysql \
  mysql:8.4
```
`-p 127.0.0.1:3306:3306`은 내 컴퓨터에서만 접속하게 하고, `-v jp-mysql-data:…`는 컨테이너를 지워도 데이터가 남게 한다.

`jplanner` 계정과 권한 (처음 한 번, root로 접속해서):
```sql
CREATE USER IF NOT EXISTS 'jplanner'@'%' IDENTIFIED BY '<비밀번호>';   -- .env의 JP_DB_PASSWORD·JP_TEST_DB_PASSWORD
CREATE DATABASE IF NOT EXISTS jp;
GRANT ALL PRIVILEGES ON jp.* TO 'jplanner'@'%';
GRANT ALL PRIVILEGES ON jp_test.* TO 'jplanner'@'%';
GRANT ALL PRIVILEGES ON jp_test_migration.* TO 'jplanner'@'%';
```
`jp_test_migration`은 US-32 마이그레이션 테스트가 V1부터 다시 쌓아 보는 빈 DB다. 권한이 없으면 그 테스트 하나가 `Access denied`로 실패한다.

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

### 로컬에서 가입해 보기 (US-33)
메일은 아직 실제로 보내지 않는다 (도메인 구입 전, D-074). 가입하면 서버 로그에 아래처럼 링크가 찍힌다.
```
[가짜 메일] 가입 인증 from=no-reply@awesomedesk.co.kr to=ne***@e***.com — 링크: http://localhost:3000/signup/verify?token=…
```
이 링크를 브라우저에 붙여 넣으면 FE 인증 화면이 열린다 (FE 화면이 없으면 `POST /api/v1/auth/signup/verify {"token":"…"}`를 Swagger UI에서 직접). 링크는 1회용·24시간.

### 로컬 로그인 계정 (`JP_DEV_ACCOUNTS`)
가입(US-33)·비밀번호 찾기(US-35)가 생기기 전까지 `.env`의 `JP_DEV_ACCOUNTS=이메일:비밀번호;이메일:비밀번호`(비밀번호 15자 이상)로 로컬 계정을 만든다. 서버가 켜질 때 없는 회원은 만들고, **비밀번호는 DB에 없을 때만** 넣는다.

그래서 `.env`에서 비밀번호만 바꾸면 반영되지 않는다. 바꾸려면 그 회원의 `password_credentials` 행을 지우고 서버를 다시 켠다.
```bash
# MySQL이 Docker면 (컨테이너 이름은 docker ps)
docker exec -it <MySQL 컨테이너> mysql -u jplanner -p jp \
  -e "DELETE FROM password_credentials WHERE user_id = (SELECT user_id FROM users WHERE email = 'me@example.com');"

# MySQL을 컴퓨터에 직접 설치했으면
mysql -u jplanner -p jp -e "DELETE FROM password_credentials WHERE user_id = (SELECT user_id FROM users WHERE email = 'me@example.com');"

# 그다음 서버 재시작 → .env의 새 비밀번호가 들어간다
```

### 서버·테스트
```bash
./gradlew bootRun   # http://localhost:8080 (Swagger UI: /swagger-ui.html)
./gradlew test      # MySQL·Redis가 켜져 있어야 한다
```
