# MapStruct 설정 및 사용 가이드

## 개요
MapStruct는 컴파일 타임에 타입 안전한 Bean 매핑 코드를 자동 생성하는 Java 어노테이션 프로세서입니다.
J-planner 프로젝트에서는 Entity와 DTO 간의 변환을 위해 MapStruct를 사용합니다.

---

## 1. 설정 내용

### 1.1 의존성 추가 (`build.gradle`)

```gradle
// mapstruct
implementation 'org.mapstruct:mapstruct:1.5.5.Final'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'
```

**각 의존성의 역할:**

| 의존성 | 역할 |
|--------|------|
| `mapstruct` | MapStruct 런타임 라이브러리 (어노테이션 및 인터페이스 제공) |
| `mapstruct-processor` | 컴파일 타임에 매퍼 구현체를 생성하는 어노테이션 프로세서 |
| `lombok-mapstruct-binding` | Lombok과 MapStruct의 어노테이션 프로세서 간 충돌 방지 |

### 1.2 주의사항
- **Lombok과의 호환성**: `lombok-mapstruct-binding`은 Lombok의 `@Getter`, `@Setter`, `@Builder` 등이 MapStruct보다 먼저 처리되도록 보장합니다.
- **빌드 순서**: 어노테이션 프로세서 순서가 중요하므로 Lombok과 MapStruct를 함께 사용할 때 반드시 바인딩 라이브러리를 포함해야 합니다.

---

## 2. 생성된 Mapper

### 2.1 CalendarMapper 인터페이스

**파일 위치:**
`src/main/java/com/awesomedesk/j_planner/v1/calendar/mapper/CalendarMapper.java`

```java
@Mapper(componentModel = "spring")
public interface CalendarMapper {

    @Mapping(source = "dateDto.startDateTime", target = "startDatetime")
    @Mapping(source = "dateDto.endDateTime", target = "endDatetime")
    CalenderInfoDto toInfoDto(Calendar calendar);

    List<CalenderInfoDto> toInfoDtoList(List<Calendar> calendars);
}
```

### 2.2 어노테이션 설명

#### `@Mapper(componentModel = "spring")`
- MapStruct가 Spring 컴포넌트로 매퍼를 생성하도록 지정
- 생성된 구현체는 `@Component`가 자동 부여되어 Spring 컨테이너에 등록됨
- `@Autowired` 또는 생성자 주입으로 사용 가능

#### `@Mapping`
- Entity와 DTO 간 필드명이 다르거나 중첩된 객체 필드를 매핑할 때 사용
- `source`: 원본 객체의 필드 경로 (점 표기법으로 중첩 필드 접근 가능)
- `target`: 대상 객체의 필드명

**예시 매핑:**
```
Calendar.dateDto.startDateTime → CalenderInfoDto.startDatetime
Calendar.dateDto.endDateTime   → CalenderInfoDto.endDatetime
```

---

## 3. 매핑 원리

### 3.1 필드 매핑 규칙

MapStruct는 다음 순서로 필드를 매핑합니다:

1. **명시적 매핑** (`@Mapping` 어노테이션 사용)
2. **이름 기반 자동 매핑** (필드명이 동일한 경우)
3. **타입 변환** (호환 가능한 타입 간 자동 변환)

### 3.2 Calendar → CalenderInfoDto 매핑 예시

**Entity (Calendar):**
```java
@Entity
public class Calendar extends BaseEntity {
    private Long id;                    // Long
    private String title;               // String
    private boolean isAllDay;           // boolean
    private DateDto dateDto;            // 중첩 객체
    private String color;               // String
}
```

**Embedded Object (DateDto):**
```java
@Embeddable
public class DateDto {
    private LocalDateTime startDateTime;  // 중첩 필드
    private LocalDateTime endDateTime;    // 중첩 필드
}
```

**DTO (CalenderInfoDto):**
```java
@Builder
public class CalenderInfoDto {
    private long id;                      // long (타입 자동 변환)
    private String title;                 // 이름 동일 → 자동 매핑
    private boolean isAllDay;             // 이름 동일 → 자동 매핑
    private LocalDateTime startDatetime;  // @Mapping 명시
    private LocalDateTime endDatetime;    // @Mapping 명시
    private String color;                 // 이름 동일 → 자동 매핑
}
```

### 3.3 컴파일 타임 생성 코드

MapStruct는 컴파일 시 다음과 같은 구현체를 자동 생성합니다:

**생성 위치:**
`build/generated/sources/annotationProcessor/java/main/`

```java
@Component
public class CalendarMapperImpl implements CalendarMapper {

    @Override
    public CalenderInfoDto toInfoDto(Calendar calendar) {
        if (calendar == null) {
            return null;
        }

        CalenderInfoDto.CalenderInfoDtoBuilder builder = CalenderInfoDto.builder();

        // 중첩 객체 필드 매핑
        builder.startDatetime(calendarDateDtoStartDateTime(calendar));
        builder.endDatetime(calendarDateDtoEndDateTime(calendar));

        // 자동 매핑 필드
        builder.id(calendar.getId());
        builder.title(calendar.getTitle());
        builder.isAllDay(calendar.isAllDay());
        builder.color(calendar.getColor());

        return builder.build();
    }

    private LocalDateTime calendarDateDtoStartDateTime(Calendar calendar) {
        if (calendar == null || calendar.getDateDto() == null) {
            return null;
        }
        return calendar.getDateDto().getStartDateTime();
    }

    private LocalDateTime calendarDateDtoEndDateTime(Calendar calendar) {
        if (calendar == null || calendar.getDateDto() == null) {
            return null;
        }
        return calendar.getDateDto().getEndDateTime();
    }

    @Override
    public List<CalenderInfoDto> toInfoDtoList(List<Calendar> calendars) {
        if (calendars == null) {
            return null;
        }

        List<CalenderInfoDto> list = new ArrayList<>(calendars.size());
        for (Calendar calendar : calendars) {
            list.add(toInfoDto(calendar));
        }
        return list;
    }
}
```

---

## 4. 사용 방법

### 4.1 Service Layer에서 사용

```java
@Service
@RequiredArgsConstructor
public class CalenderServiceImpl implements CalenderService {

    private final CalenderRepository calenderRepository;
    private final CalendarMapper calendarMapper;  // Spring이 자동 주입

    public CalenderInfoDto getCalendar(Long id) {
        Calendar calendar = calenderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Calendar not found"));

        // Mapper를 통한 변환
        return calendarMapper.toInfoDto(calendar);
    }

    public List<CalenderInfoDto> getAllCalendars() {
        List<Calendar> calendars = calenderRepository.findAll();

        // List 변환
        return calendarMapper.toInfoDtoList(calendars);
    }
}
```

### 4.2 Controller Layer 예시

```java
@RestController
@RequestMapping("/api/v1/calendars")
@RequiredArgsConstructor
public class CalendarController {

    private final CalenderService calenderService;

    @GetMapping("/{id}")
    public AwesomeResponse<CalenderInfoDto> getCalendar(@PathVariable Long id) {
        CalenderInfoDto dto = calenderService.getCalendar(id);
        return AwesomeResponse.ok(dto);
    }

    @GetMapping
    public AwesomeResponse<List<CalenderInfoDto>> getAllCalendars() {
        List<CalenderInfoDto> dtos = calenderService.getAllCalendars();
        return AwesomeResponse.ok(dtos);
    }
}
```

---

## 5. MapStruct 고급 기능

### 5.1 커스텀 매핑 메서드

복잡한 변환 로직이 필요한 경우 `default` 메서드나 `@AfterMapping`을 사용할 수 있습니다.

```java
@Mapper(componentModel = "spring")
public interface CalendarMapper {

    @Mapping(source = "dateDto.startDateTime", target = "startDatetime")
    @Mapping(source = "dateDto.endDateTime", target = "endDatetime")
    CalenderInfoDto toInfoDto(Calendar calendar);

    // 커스텀 변환 로직
    default String formatColor(String color) {
        return color != null ? color.toUpperCase() : "#FFFFFF";
    }

    // 매핑 후 추가 처리
    @AfterMapping
    default void enrichDto(@MappingTarget CalenderInfoDto dto, Calendar calendar) {
        // 특정 조건에 따른 후처리 로직
        if (dto.isAllDay()) {
            dto.setStartDatetime(dto.getStartDatetime().toLocalDate().atStartOfDay());
        }
    }
}
```

### 5.2 양방향 매핑

DTO를 Entity로 역변환하는 메서드도 추가 가능합니다.

```java
@Mapper(componentModel = "spring")
public interface CalendarMapper {

    // Entity → DTO
    @Mapping(source = "dateDto.startDateTime", target = "startDatetime")
    @Mapping(source = "dateDto.endDateTime", target = "endDatetime")
    CalenderInfoDto toInfoDto(Calendar calendar);

    // DTO → Entity
    @Mapping(target = "dateDto.startDateTime", source = "startDatetime")
    @Mapping(target = "dateDto.endDateTime", source = "endDatetime")
    @Mapping(target = "id", ignore = true)  // ID는 DB가 생성
    Calendar toEntity(CalenderInfoDto dto);
}
```

### 5.3 Null 값 처리 전략

```java
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS
)
public interface CalendarMapper {
    // Null 값이 있는 필드는 무시하고 기존 값 유지
    CalenderInfoDto toInfoDto(Calendar calendar);
}
```

---

## 6. 빌드 및 확인

### 6.1 프로젝트 빌드

```bash
./gradlew clean build
```

### 6.2 생성된 구현체 확인

생성된 Mapper 구현체는 다음 경로에서 확인할 수 있습니다:

```
build/generated/sources/annotationProcessor/java/main/
  └── com/awesomedesk/j_planner/v1/calendar/mapper/
      └── CalendarMapperImpl.java
```

### 6.3 IDE 설정 (IntelliJ IDEA)

1. **Annotation Processing 활성화**
   - `Settings` → `Build, Execution, Deployment` → `Compiler` → `Annotation Processors`
   - `Enable annotation processing` 체크

2. **생성된 소스 디렉토리 인식**
   - `build/generated/sources/annotationProcessor/java/main` 디렉토리가 자동으로 소스 루트로 인식됨
   - 빌드 후 생성된 `*MapperImpl` 클래스가 IDE에서 참조 가능

---

## 7. 장점 정리

| 항목 | 설명 |
|------|------|
| **성능** | 컴파일 타임에 코드 생성 → 런타임 리플렉션 없음 |
| **타입 안전성** | 컴파일 시점에 타입 검증 → 런타임 오류 방지 |
| **유지보수** | 필드 추가/변경 시 컴파일 에러로 누락 방지 |
| **가독성** | 인터페이스만 정의 → 보일러플레이트 코드 제거 |
| **디버깅** | 생성된 구현체 코드를 직접 확인 가능 |
| **Spring 통합** | `componentModel = "spring"`으로 DI 지원 |

---

## 8. 참고 자료

- [MapStruct 공식 문서](https://mapstruct.org/)
- [MapStruct GitHub](https://github.com/mapstruct/mapstruct)
- [MapStruct + Lombok 설정 가이드](https://mapstruct.org/faq/#Can-I-use-MapStruct-together-with-Project-Lombok)

---

## 9. 문제 해결 (Troubleshooting)

### 9.1 Mapper 구현체가 생성되지 않는 경우

**증상:**
```
Could not autowire. No beans of 'CalendarMapper' type found.
```

**해결 방법:**
1. Gradle 의존성이 올바르게 추가되었는지 확인
2. `./gradlew clean build` 실행
3. IDE의 Annotation Processing이 활성화되어 있는지 확인
4. `build/generated/sources` 디렉토리 확인

### 9.2 Lombok과 충돌하는 경우

**증상:**
```
Unknown property "startDateTime" in type DateDto
```

**해결 방법:**
- `lombok-mapstruct-binding` 의존성 확인
- `annotationProcessor` 순서 확인 (Lombok이 먼저 처리되어야 함)

### 9.3 Nested 필드 매핑 오류

**증상:**
```
Can't map property "dateDto.startDateTime" to "startDatetime"
```

**해결 방법:**
- `DateDto` 클래스에 `@Getter` 어노테이션이 있는지 확인
- `@Mapping` 어노테이션의 `source` 경로가 정확한지 확인

---

**작성일:** 2025-10-11
**프로젝트:** J-planner-BE
**MapStruct 버전:** 1.5.5.Final
