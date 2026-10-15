# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

J-planner is a Spring Boot application designed for MBTI 'J' personality types, providing calendar, planner, to-do, and diary functionality. The project uses **Java 25, Spring Boot 4.1, Gradle 9.7** (Hibernate 7, Jackson 3 — `tools.jackson.*` packages), JPA, MySQL 8.4, and AOP logging.

## Product Planning

- Product planning docs (requirements, decision log, open questions, implementation status) live in a **separate repository**, not here.
  - Local: `../j-planner-product`
  - GitHub: https://github.com/awesomedesk/j-planner-product (**public**, D-062)
- **Never commit secrets or personal data to any repository** (passwords, tokens, API keys, `.env` values, real emails/names, server IPs/VPN config). Use fake examples (`example.com`, `<secret>`). Real values come from environment variables (production: GitHub Actions Secrets per environment, D-062). The admin account email for the V2 migration comes from `JP_ADMIN_EMAIL` (Flyway placeholder `admin_email`).
- Before implementing a feature, check `../j-planner-product/02-requirements.md` (requirement IDs, status) and `03-decisions.md` (confirmed decisions).
- Do not add planning documents to this repository.

## Build Commands

- **Build**: `./gradlew build`
- **Run application**: `./gradlew bootRun` 
- **Run tests**: `./gradlew test`
- **Clean**: `./gradlew clean`

The project uses Gradle with wrapper scripts (`gradlew` for Unix/Mac, `gradlew.bat` for Windows).

## Architecture

### Package Structure
- `com.awesomedesk.j_planner` - Root package
  - `api/v1/<feature>/` - One folder per API resource (Controller, Service, Repository, entity, `…Request`/`…Response` records). Implemented: `health`, `category`, `schedule`, `todo`, `dday`, `diary`, `memo`, `settings`, `user` (`UserDataInitializer`: new member's default rows)
  - `common/`
    - `auth/` - `AuthUser` (logged-in member, Spring Security principal; name = user_id), `AuthUserArgumentResolver`, `DeviceSessions` (login session = device: new session id on login, max 10 devices, log out others), `SessionPolicyInterceptor` (30-day max, re-check suspension every minute)
    - `audit/` - `AuditLogger` → `audit_logs` (no email/password/body in it). `log/EmailMasker` for logs
    - `mail/` - `AuthMailer` (account mails). Only `LoggingAuthMailer` for now (fake: logs the link, **not created in `prd`** so prod cannot start without a real mailer). Real sending (Amazon SES) comes with the domain/deploy work (D-074)
    - `ratelimit/` - `RateLimits` (Redis fixed-window counters `jp:rl:<rule>:<hash>`; `hit` → 429, `tryHit` → false)
    - `api/` - `JsonMergePatch` (PATCH per RFC 7396), `RequestValidator`, `DateRanges` (62-day query limit), `Positions` (move/reorder), `PositionRequest`
    - `error/` - Error handling: `ErrorCode`, `ApiException`, `GlobalExceptionHandler` (RFC 9457 Problem Details)
    - `domain/` - `BaseEntity` (created/updated/deleted columns, soft delete)
    - `aop/logger/` - AOP-based logging
    - `converter/attribute/` - JPA attribute converters (Y/N ↔ boolean)
  - `config/` - `SecurityConfig` (login required on `/api/**` except health and `/api/v1/auth/**` → 401; CSRF SPA cookie `__Host-XSRF-TOKEN` + header `X-XSRF-TOKEN` → 403 `CSRF_INVALID`), `SessionConfig` (Spring Session + Redis, namespace `jp:session`, cookie `__Host-JP_SID`, 14-day idle), `FlywayConfig`, CORS (`app.cors.allowed-origins`), `Cache-Control: no-store`, `Clock` (Asia/Seoul), JPA auditing

### Key Architectural Patterns
- **Pure REST responses (D-031)**: Success returns the HTTP status + resource JSON directly (no wrapper). Errors are `application/problem+json` (RFC 9457) with extra `code` and `errors` properties. Throw `ApiException(ErrorCode, detail)` from services; `GlobalExceptionHandler` converts it. API spec: `../j-planner-product/08-api-design.md`, `08-openapi.yaml`
- **Layered Architecture**: Controller → Service → Repository pattern with clear separation
- **Per-user data (US-32, 08 13-2)**: controllers take an `AuthUser` parameter and pass `user.userId()` to services. Every repository query has a `userId` condition (`findByIdAndUserId` …). Another member's id → 404 `NOT_FOUND`; another member's `categoryId`/`afterId` → 400. Never read userId from the request
- **AOP Logging**: `LoggerAspect` logs controller (debug) / service (trace) calls with value *shapes* only — never request/response bodies (diary/memo content)
- **Sortable**: Category/Todo/D-Day implement `Sortable`; moves use `Positions.reorder`
- **JPA Auditing**: Enabled with `@EnableJpaAuditing` for automatic timestamp tracking
- **Profile-based Configuration**: Separate config files for local/test/prod environments

### Configuration
- **Profiles**: Uses `application-local.yml`, `application-tst.yml`, `application-prd.yml`
- **Default Profile**: `local`
- **Database**: MySQL with JPA/Hibernate
- **Port**: 8080
- **Context Path**: `/`

### Local DB
- Tables are managed by **Flyway**: `src/main/resources/db/migration/V*.sql` (V1 = tables + required seed rows). Applied on startup; never edit an applied file — add `V2__desc.sql`. A DB with tables but no history (old local DB, local-reset) is baselined as V1
- Reset local `jp` DB (drops everything, recreates tables from V1, loads sample data):
  `cd src/main/resources/sql && mysql -u jplanner -p < local-reset.sql`. `sample-data.sql` = local-only test data (dates around 2026-09-25)
- JDBC URLs use `connectionTimeZone=Asia/Seoul&preserveInstants=false` (DATETIME stored/read as-is) and `sessionVariables=time_zone='%2B09:00'` (DB-side `NOW()` is KST even if the DB server is UTC)
- `JP_ADMIN_EMAIL` (`.env`) is required: V2 creates the admin member with it and moves the existing data to that member (blank → startup fails)
- Sign-up (US-33): `POST /api/v1/auth/signup` → `pending_signups` + mail → link `${app.web.base-url}/signup/verify?token=…` (FE page) → `POST /api/v1/auth/signup/verify` creates the member (no auto-login). Locally the mail is only logged (`[가짜 메일]` line with the link). Leaked-password check calls HIBP range API (`app.auth.hibp.enabled`, 2 s timeout, fail-open). Expired pending rows: `PendingSignupCleaner` hourly
- Login (US-34): `POST /api/v1/auth/login` after `GET /api/v1/auth/csrf`. Local accounts until sign-up (US-33)/password reset (US-35): `JP_DEV_ACCOUNTS=email:password;email2:password2` in `.env` → `DevAccounts` (local profile only) creates missing members and sets a password only if none
- Redis (sessions, rate limits) must run locally on 127.0.0.1:6379 — **Docker, Redis 8.2** (README "로컬 실행"):
  `docker run -d --name jp-redis --restart unless-stopped -p 127.0.0.1:6379:6379 redis:8.2-alpine --save "" --appendonly no`.
  Bind to 127.0.0.1 only (no password locally), no persistence (just log in again). 8.2 = the Extended (LTS-like) 8.x release; production uses 8.2 too. Code uses only basic commands (INCR, EXPIRE, SET) — no 8.8+ commands such as INCREX. Stop brew Redis first if it runs (`brew services stop redis`). `tst`/`prd` read `JP_REDIS_HOST`, `JP_REDIS_PORT`, `JP_REDIS_USERNAME`, `JP_REDIS_PASSWORD`
- Secrets: local DB password comes from `.env` in the project root (git-ignored, imported via `spring.config.import: optional:file:.env[.properties]`), e.g. `JP_DB_PASSWORD=...`, `JP_TEST_DB_PASSWORD=...`. `tst`/`prd` read `JP_DB_URL`, `JP_DB_USERNAME`, `JP_DB_PASSWORD`, `JP_CORS_ORIGINS`, `JP_WEB_BASE_URL` (FE address for mail links, no default) from the environment. `JP_MAIL_FROM` defaults to `no-reply@awesomedesk.co.kr`

- Local profile writes a request log (method, path, status, ms) to `logs/access.YYYY-MM-DD.log` — use it to check what the server actually returned

### API spec / Swagger UI
- The API contract is written first in `../j-planner-product/08-openapi.yaml` (contract-first; FE generates types from it). Code does NOT generate the spec
- Build copies it to `static/openapi.yaml` (`processResources`; override with `-PopenapiSpec=path`). The build fails if the file is missing → check out `j-planner-product` next to this repo (CI too)
- Swagger UI: http://localhost:8080/swagger-ui.html shows that file (springdoc, off in `prd`)
- Every integration/acceptance test response is validated against the spec (`support/OpenApiContract`, atlassian swagger-request-validator; requests are not validated). If a test fails with `validation.response...`, fix the spec or the code — they disagree

### Testing (TDD)
Work test-first (08-api-design.md 12-1): turn each acceptance criterion (AC) in `j-planner-product/09-backlog.md` into a failing test, commit it (`Test: … (빨강)`), then implement until green (`Feat:`), then clean up (`Refactor:`).
- **Acceptance tests** `acceptance/USxx…AcceptanceTest` extend `AcceptanceTest`: one class per story, one test per AC, `@DisplayName("ACn: <AC sentence>")`. FE-only ACs are listed as "FE 담당" in the class Javadoc. Helpers: `postJson`, `createCategory`, `createTodo`, `read(result, "$.path")` …
- **API tests** `api/v1/<feature>/…ApiTest`: detailed rules from 08 (validation, query combinations)
- **Unit tests** (no DB, fast): rule classes such as `TodoDateRule`, `ScheduleTimes`, `Positions`, `DateRanges`, `JsonMergePatch`, entities. Use `support/ApiExceptionAssertions` for error code / field checks. Put rules in small classes so they can be unit tested
- Requests in API/acceptance tests are sent as the admin member (user_id 1, `LoginUserConfig`) with a CSRF cookie + header. Other member: `createUser(email)` + `.with(as(id))`; no login: `.with(anonymous())`; password: `setPassword(id, raw)`. Don't use spring-security-test `csrf()` — it swaps the shared CSRF store to a session one and breaks the cookie-flow tests
- Real login/session/CSRF flow: extend `BrowserAcceptanceTest` and use `browser(ip)` / `loggedIn(...)` (`Browser` keeps cookies, MockMvc without defaults) — `US33SignupAcceptanceTest`, `US34LoginAcceptanceTest`
- Mails are not sent in tests: `RecordingMailer` (`mailer.sentTo(email)`, link in each mail); leaked passwords = `FakeBreachedPasswords.LEAKED` (`AuthTestConfig`, @Primary)
- Needs local Redis on 127.0.0.1:6379 too. Tests use Redis **DB 1** (`src/test/resources/application.yml`, `JP_TEST_REDIS_DATABASE`) and flush it before each integration test; the local server uses DB 0, so running tests does not log you out
- API/acceptance tests extend `support/IntegrationTest`: real MySQL test DB `jp_test` (created automatically), tables from Flyway migrations (`TestFlywayConfig` cleans and migrates `jp_test` at startup), data reset before each test by `src/test/resources/sql/reset.sql`. "Now" is fixed to 2026-09-25 (Fri) 09:00 KST by `FixedClockConfig`; week starts on Sunday
- Needs a local MySQL on 127.0.0.1:3306 (the owner runs it in **Docker**: `docker exec -it <container> mysql ...`, README "MySQL (Docker)"), and the test user needs ALL on `jp_test.*` **and `jp_test_migration.*`** (`US32UserDataAcceptanceTest` AC3 replays V1 → latest in that separate DB; URL = test URL with `/jp_test?` → `/jp_test_migration?`). Grant SQL in README. Override with `JP_TEST_DB_URL`, `JP_TEST_DB_USERNAME`, `JP_TEST_DB_PASSWORD`
- Error shape / CORS tests use `@WebMvcTest` (`org.springframework.boot.webmvc.test.autoconfigure`)
- If a test uncovers a case the planning docs don't decide, ask PO instead of choosing

### Dependencies
- Spring Boot Starter (Web, Data JPA, AOP)
- MySQL Connector
- Lombok for boilerplate code reduction
- SLF4J + Logback for logging
- JUnit 5 for testing

## Development Notes

- Application entry point: `J_plannerApiApplication.java`
- All entities extend `BaseEntity` for common audit fields
- Controllers follow REST conventions (08-api-design.md)
- Request/Response DTOs are records named `*Request` / `*Response`
- Services are concrete classes (no interface/impl split)