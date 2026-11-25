package com.awesomedesk.j_planner.api.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.util.ReflectionUtils;

/**
 * US-44 · #388 C-2 · #398 2번: 회원 데이터 저장소(Repository)는 회원 조건 없이 읽거나 바꾸지 못한다 — DB 없이 코드만 본다.
 * <p>
 * 대상 = {@code src/main/java}의 JpaRepository 중 엔티티에 {@code userId} 필드가 있는 것 전부 (새로 만들면 자동으로 들어온다).
 * <ol>
 *   <li>저장소에 직접 쓴 메서드는 {@code userId} 매개변수를 받고, {@code @Query}면 쿼리에 {@code :userId}, 이름으로 만드는 쿼리면 이름에 {@code UserId}가 있다</li>
 *   <li>서비스 코드가 부르는 물려받은 메서드(findById·findAll·deleteById …)는 이미 회원 조건으로 꺼낸 엔티티를 다루는 것(save·delete·flush)만</li>
 * </ol>
 * 예외는 아래 두 목록에 이유와 함께 둔다. JdbcTemplate으로 쓰는 회원 표(sidebar_items 등)는 이 검사 밖이다.
 */
@DisplayName("US-44 회원 데이터 저장소의 userId 조건 (정적 검사)")
class UserDataRepositoryRuleTest {

    private static final Path MAIN = Path.of("src/main/java");

    /** 회원 조건이 없어도 되는 저장소 메서드와 이유 */
    private static final Map<String, String> METHOD_EXCEPTIONS = Map.of(
        "CategoryRepository.moveSchedules", "카테고리 삭제 때만 부른다 — from은 바로 앞에서 findByIdAndUserId로 내 것임을 확인한 카테고리, to는 내 기본 카테고리 (CategoryService.delete)",
        "CategoryRepository.moveTodos", "위와 같음");

    /** 물려받은 메서드 중 회원 조건이 없어도 되는 부름과 이유 */
    private static final Map<String, String> CALL_EXCEPTIONS = Map.of(
        "UserSettingsRepository.findById", "user_settings의 기본 키가 user_id다 — findById(userId)가 곧 회원 조건",
        "CategoryRepository.getReferenceById", "카테고리 삭제 때 위 UPDATE가 영속성 컨텍스트를 비운 뒤 같은 id를 다시 참조할 뿐 — id는 바로 앞에서 findByIdAndUserId로 확인 (CategoryService.delete)");

    /** 이미 회원 조건으로 꺼낸 엔티티를 저장·삭제하는 메서드 */
    private static final Set<String> SAFE_INHERITED = Set.of("save", "saveAndFlush", "delete", "flush");

    private static List<Class<?>> repositories;
    private static List<Path> sources;

    @BeforeAll
    static void findRepositories() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        repositories = new ArrayList<>();
        for (Path file : sources) {
            if (read(file).matches("(?s).*\\binterface\\s+\\w+\\s+extends\\s+JpaRepository<.*")) {
                Class<?> type = Class.forName(MAIN.relativize(file).toString().replace(".java", "").replace('/', '.'));
                if (ReflectionUtils.findField(entityOf(type), "userId") != null) {
                    repositories.add(type);
                }
            }
        }
    }

    @Test
    @DisplayName("검사 대상은 회원 데이터 저장소 전부다 (카테고리·일정·Todo·D-Day·일기·메모·설정)")
    void coversDataRepositories() {
        assertThat(repositories).extracting(Class::getSimpleName).contains(
            "CategoryRepository", "ScheduleRepository", "TodoRepository", "DdayRepository",
            "DiaryRepository", "MemoRepository", "UserSettingsRepository");
    }

    @Test
    @DisplayName("저장소에 쓴 메서드는 모두 userId를 받고 쿼리 조건에 쓴다 (예외: METHOD_EXCEPTIONS)")
    void declaredMethodsTakeUserId() {
        Map<String, String> problems = new TreeMap<>();
        Set<String> declared = new TreeSet<>();
        for (Class<?> repository : repositories) {
            for (Method method : repository.getDeclaredMethods()) {
                if (method.isSynthetic()) {
                    continue;
                }
                String name = repository.getSimpleName() + "." + method.getName();
                declared.add(name);
                String problem = userIdProblem(method);
                if (problem != null && !METHOD_EXCEPTIONS.containsKey(name)) {
                    problems.put(name, problem);
                }
            }
        }
        assertThat(problems).as("회원 조건이 없는 저장소 메서드").isEmpty();
        assertThat(declared).as("예외 목록은 지금 있는 메서드만").containsAll(METHOD_EXCEPTIONS.keySet());
    }

    @Test
    @DisplayName("서비스 코드는 회원 조건 없는 물려받은 메서드(findById·findAll·deleteById …)를 부르지 않는다 (예외: CALL_EXCEPTIONS)")
    void noUnscopedInheritedCalls() throws IOException {
        Map<String, Class<?>> bySimpleName = new TreeMap<>();
        repositories.forEach(r -> bySimpleName.put(r.getSimpleName(), r));
        Set<String> problems = new TreeSet<>();
        Set<String> calls = new TreeSet<>();
        for (Path file : sources) {
            String source = read(file);
            Matcher field = Pattern.compile("\\b(\\w+Repository)\\s+(\\w+)\\s*[;,)=]").matcher(source);
            while (field.find()) {
                Class<?> repository = bySimpleName.get(field.group(1));
                if (repository == null) {
                    continue;
                }
                Set<String> own = new TreeSet<>();
                Arrays.stream(repository.getDeclaredMethods()).forEach(m -> own.add(m.getName()));
                Matcher call = Pattern.compile("\\b" + field.group(2) + "\\.(\\w+)\\(").matcher(source);
                while (call.find()) {
                    String method = call.group(1);
                    String name = repository.getSimpleName() + "." + method;
                    calls.add(name);
                    if (!own.contains(method) && !SAFE_INHERITED.contains(method) && !CALL_EXCEPTIONS.containsKey(name)) {
                        problems.add(name + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertThat(problems).as("회원 조건 없는 물려받은 메서드 부름").isEmpty();
        assertThat(calls).as("예외 목록은 지금 있는 부름만").containsAll(CALL_EXCEPTIONS.keySet());
    }

    @Test
    @DisplayName("검사 규칙 자체: userId 없는 메서드·쿼리에 :userId가 없는 메서드·이름에 UserId가 없는 메서드를 잡는다")
    void ruleCatchesUnscopedMethods() throws Exception {
        assertThat(userIdProblem(Bad.class.getMethod("findByTitle", String.class))).isNotNull();
        assertThat(userIdProblem(Bad.class.getMethod("findAllMine", Long.class))).isNotNull();
        assertThat(userIdProblem(Bad.class.getMethod("findByTitle", String.class, Long.class))).isNotNull();
        assertThat(userIdProblem(Bad.class.getMethod("findByIdAndUserId", Long.class, Long.class))).isNull();
        assertThat(userIdProblem(Bad.class.getMethod("findMine", Long.class))).isNull();
    }

    /** 문제가 없으면 null */
    private static String userIdProblem(Method method) {
        boolean takesUserId = Arrays.stream(method.getParameters()).map(Parameter::getName).anyMatch("userId"::equals);
        if (!takesUserId) {
            return "userId 매개변수 없음";
        }
        Query query = method.getAnnotation(Query.class);
        if (query != null) {
            return query.value().contains(":userId") ? null : "@Query에 :userId 조건 없음";
        }
        if (!method.isDefault() && !method.getName().contains("UserId")) {
            return "이름으로 만드는 쿼리인데 이름에 UserId 없음";
        }
        return null;
    }

    private static Class<?> entityOf(Class<?> repository) {
        for (Type type : repository.getGenericInterfaces()) {
            if (type instanceof ParameterizedType p && p.getRawType() == JpaRepository.class) {
                return (Class<?>) p.getActualTypeArguments()[0];
            }
        }
        throw new IllegalStateException(repository.getName());
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 규칙이 잡아야 하는 예 (실제 저장소 아님) */
    @SuppressWarnings("unused")
    interface Bad {
        Object findByTitle(String title);

        @Query("select x from X x")
        Object findAllMine(Long userId);

        Object findByTitle(String title, Long userId);

        Object findByIdAndUserId(Long id, Long userId);

        @Query("select x from X x where x.userId = :userId")
        Object findMine(Long userId);
    }
}
