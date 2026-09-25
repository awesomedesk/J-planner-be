# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

J-planner is a Spring Boot application designed for MBTI 'J' personality types, providing calendar, planner, to-do, and diary functionality. The project uses **Java 25, Spring Boot 4.1, Gradle 9.7** (Hibernate 7, Jackson 3 — `tools.jackson.*` packages), JPA, MySQL 8.4, and AOP logging.

## Product Planning

- Product planning docs (requirements, decision log, open questions, implementation status) live in a **separate repository**, not here.
  - Local: `../j-planner-product`
  - GitHub: https://github.com/awesomedesk/j-planner-product (private)
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
  - `api/v1/<feature>/` - One folder per API resource (Controller, Service, Repository, entity, `…Request`/`…Response` records). Implemented: `health`, `category`, `schedule`
  - `common/`
    - `api/` - `JsonMergePatch` (PATCH per RFC 7396), `RequestValidator`, `DateRanges` (62-day query limit), `Positions` (move/reorder), `PositionRequest`
    - `error/` - Error handling: `ErrorCode`, `ApiException`, `GlobalExceptionHandler` (RFC 9457 Problem Details)
    - `domain/` - `BaseEntity` (created/updated/deleted columns, soft delete)
    - `aop/logger/` - AOP-based logging
    - `converter/attribute/` - JPA attribute converters (Y/N ↔ boolean)
  - `config/` - CORS (`app.cors.allowed-origins`), `Cache-Control: no-store`, `Clock` (Asia/Seoul), JPA auditing

### Key Architectural Patterns
- **Pure REST responses (D-031)**: Success returns the HTTP status + resource JSON directly (no wrapper). Errors are `application/problem+json` (RFC 9457) with extra `code` and `errors` properties. Throw `ApiException(ErrorCode, detail)` from services; `GlobalExceptionHandler` converts it. API spec: `../j-planner-product/08-api-design.md`, `08-openapi.yaml`
- **Layered Architecture**: Controller → Service → Repository pattern with clear separation
- **AOP Logging**: `LoggerAspect` provides cross-cutting logging functionality
- **JPA Auditing**: Enabled with `@EnableJpaAuditing` for automatic timestamp tracking
- **Profile-based Configuration**: Separate config files for local/test/prod environments

### Configuration
- **Profiles**: Uses `application-local.yml`, `application-tst.yml`, `application-prd.yml`
- **Default Profile**: `local`
- **Database**: MySQL with JPA/Hibernate
- **Port**: 8080
- **Context Path**: `/`

### Local DB
- Reset local `jp` DB (drops everything, recreates tables, loads sample data):
  `cd src/main/resources/sql && mysql -u jplanner -p < local-reset.sql`
- `schema.sql` = tables + required seed rows only (also used by tests and production). `sample-data.sql` = local-only test data (dates around 2026-09-25)
- JDBC URLs use `connectionTimeZone=Asia/Seoul&preserveInstants=false` so DATETIME values are stored/read as-is regardless of the JVM time zone

- Local profile writes a request log (method, path, status, ms) to `logs/access.YYYY-MM-DD.log` — use it to check what the server actually returned

### Testing
- API tests extend `support/IntegrationTest`: real MySQL test DB `jp_test` (created automatically), tables from `sql/schema.sql`, data reset before each test by `src/test/resources/sql/reset.sql`
- Needs a local MySQL on 127.0.0.1:3306. Override with `JP_TEST_DB_URL`, `JP_TEST_DB_USERNAME`, `JP_TEST_DB_PASSWORD`
- Error shape / CORS tests use `@WebMvcTest` (`org.springframework.boot.webmvc.test.autoconfigure`)

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