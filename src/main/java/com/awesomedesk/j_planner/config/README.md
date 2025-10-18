# Configuration Guide

이 폴더는 J-planner 애플리케이션의 설정 파일들을 포함합니다.

## 📁 파일 구조

```
config/
├── README.md              # 이 파일
└── JpaAuditingConfig.java # JPA Auditing 설정
```

---

## JpaAuditingConfig.java

JPA Auditing 기능을 활성화하여 엔티티의 생성/수정 시간을 자동으로 기록합니다.

### 현재 활성화된 기능

- `@CreatedDate` - 엔티티 생성 시간 자동 기록
- `@LastModifiedDate` - 엔티티 수정 시간 자동 기록

### 사용 방법

엔티티 클래스에서 `BaseEntity`를 상속받으면 자동으로 적용됩니다:

```java
@Entity
public class Calendar extends BaseEntity {
    // createdAt, updatedAt 필드가 자동으로 관리됨
}
```

### 추가 설정 가능 기능

#### 1. 생성자/수정자 추적

사용자 인증 기능이 추가되면 누가 생성/수정했는지 추적할 수 있습니다.

**Step 1**: `BaseEntity`에 필드 추가
```java
@MappedSuperclass
public class BaseEntity {
    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;  // 생성자

    @LastModifiedBy
    private String lastModifiedBy;  // 수정자
}
```

**Step 2**: `JpaAuditingConfig.java`에서 설정 활성화

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")  // 주석 해제
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // Spring Security 사용 시
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }
            return Optional.of(authentication.getName());
        };
    }
}
```

#### 2. 커스텀 시간대 설정

특정 시간대를 사용하고 싶다면:

```java
@Bean
public DateTimeProvider dateTimeProvider() {
    return () -> Optional.of(ZonedDateTime.now(ZoneId.of("Asia/Seoul")));
}
```

그리고 `@EnableJpaAuditing` 어노테이션 수정:
```java
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
```

---

## @EnableJpaAuditing 옵션 상세

```java
@EnableJpaAuditing(
    auditorAwareRef = "auditorProvider",     // AuditorAware Bean 이름
    setDates = true,                         // 날짜 자동 설정 (기본값: true)
    modifyOnCreate = true,                   // 생성 시에도 수정일 설정 (기본값: true)
    dateTimeProviderRef = "dateTimeProvider" // 커스텀 시간 제공자
)
```

### 옵션 설명

| 옵션 | 설명 | 기본값 |
|------|------|--------|
| `auditorAwareRef` | `@CreatedBy`, `@LastModifiedBy` 사용 시 현재 사용자를 제공하는 Bean 이름 | - |
| `setDates` | `@CreatedDate`, `@LastModifiedDate` 자동 설정 여부 | `true` |
| `modifyOnCreate` | 엔티티 생성 시 `updatedAt`도 설정할지 여부 | `true` |
| `dateTimeProviderRef` | 커스텀 날짜/시간 제공자 Bean 이름 | - |

---

## 테스트에서의 동작

테스트 환경에서도 JPA Auditing이 정상 작동합니다:

- **단위 테스트** (`@WebMvcTest`): JPA 설정 제외되어 동작하지 않음
- **통합 테스트** (`@DataJpaTest`, `@SpringBootTest`): 정상 동작
- **Repository 테스트**: `@DataJpaTest`에서 자동으로 활성화됨

테스트에서 생성/수정 시간 확인 예시:
```java
@Test
void testAuditing() {
    Calendar calendar = new Calendar();
    calendarRepository.save(calendar);

    assertThat(calendar.getCreatedAt()).isNotNull();
    assertThat(calendar.getUpdatedAt()).isNotNull();
}
```

---

## AuditorAware 구현 예시

### 1. Spring Security 사용

```java
@Bean
public AuditorAware<String> auditorProvider() {
    return () -> {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.of("system");
        }

        // Principal이 UserDetails인 경우
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return Optional.of(((UserDetails) principal).getUsername());
        }

        return Optional.of(principal.toString());
    };
}
```

### 2. HTTP Session 사용

```java
@Bean
public AuditorAware<String> auditorProvider(HttpServletRequest request) {
    return () -> {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String userId = (String) session.getAttribute("userId");
            return Optional.ofNullable(userId);
        }
        return Optional.of("anonymous");
    };
}
```

### 3. 고정값 (개발/테스트용)

```java
@Bean
public AuditorAware<String> auditorProvider() {
    return () -> Optional.of("admin");
}
```

### 4. JWT 토큰에서 추출

```java
@Bean
public AuditorAware<String> auditorProvider(JwtTokenProvider tokenProvider,
                                           HttpServletRequest request) {
    return () -> {
        String token = tokenProvider.resolveToken(request);
        if (token != null && tokenProvider.validateToken(token)) {
            return Optional.of(tokenProvider.getUserId(token));
        }
        return Optional.of("anonymous");
    };
}
```

---

## 주의사항

1. **@EntityListeners 필수**: `BaseEntity`에 `@EntityListeners(AuditingEntityListener.class)` 어노테이션이 있어야 합니다.

2. **@EnableJpaAuditing 위치**:
   - 메인 애플리케이션 클래스 또는 별도의 `@Configuration` 클래스에 선언
   - 현재는 `JpaAuditingConfig` 클래스에 분리되어 있음

3. **테스트 환경**:
   - `@WebMvcTest`는 JPA 설정을 로드하지 않으므로 Auditing이 작동하지 않음
   - `@DataJpaTest` 또는 `@SpringBootTest` 사용 시 정상 작동

4. **타임존**:
   - 기본적으로 서버의 시스템 타임존 사용
   - `application.yml`에서 `spring.jpa.properties.hibernate.jdbc.time_zone` 설정 가능

---

## 참고 자료

- [Spring Data JPA Auditing Documentation](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#auditing)
- [Baeldung - JPA Auditing Guide](https://www.baeldung.com/database-auditing-jpa)
