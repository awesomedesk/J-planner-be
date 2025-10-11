# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

J-planner is a Spring Boot application designed for MBTI 'J' personality types, providing calendar, planner, to-do, and diary functionality. The project uses Java 21 with Spring Boot 3.2.4, JPA, MySQL, and includes AOP logging.

## Build Commands

- **Build**: `./gradlew build`
- **Run application**: `./gradlew bootRun` 
- **Run tests**: `./gradlew test`
- **Clean**: `./gradlew clean`

The project uses Gradle with wrapper scripts (`gradlew` for Unix/Mac, `gradlew.bat` for Windows).

## Architecture

### Package Structure
- `com.awesomedesk.j_planner` - Root package
  - `common/` - Shared components and utilities
    - `response/` - Standardized API response handling (`AwesomeResponse`, exceptions)
    - `domain/` - Common domain objects (`BaseEntity`, `DateDto`, `Location`)
    - `controller/` - Global exception handler
    - `aop/logger/` - AOP-based logging
    - `converter/attribute/` - JPA attribute converters
  - `v1/` - API version 1
    - `calendar/` - Calendar feature (controller, service, repository, domain, dto)
    - `apiTest/` - Testing endpoints
  - `scheduler/` - Scheduled tasks

### Key Architectural Patterns
- **Standardized Responses**: All API endpoints use `AwesomeResponse<T>` wrapper for consistent response format
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

### Testing
- Uses JUnit 5 platform
- Test files located in `src/test/java/`
- Includes controller and exception handler tests

### Dependencies
- Spring Boot Starter (Web, Data JPA, AOP)
- MySQL Connector
- Lombok for boilerplate code reduction
- SLF4J + Logback for logging
- JUnit 5 for testing

## Development Notes

- Application entry point: `J_plannerApiApplication.java`
- All entities extend `BaseEntity` for common audit fields
- Controllers use `@Slf4j` for logging and follow REST conventions
- Request/Response DTOs follow naming pattern: `*ReqDto`, `*ResDto`
- Service interfaces and implementations are separated