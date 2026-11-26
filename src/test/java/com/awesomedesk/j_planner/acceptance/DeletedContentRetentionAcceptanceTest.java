package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.awesomedesk.j_planner.api.v1.user.DeletedContentPurger;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * D-125 (대표님 #410 'B', PO #425): 사용자가 지운 작성 데이터는 30일 보관한 뒤 매일 정리 작업이 완전 삭제한다.
 * <ul>
 *   <li>대상과 순서: 일정(calendar_details → calendars) · Todo · D-Day · 일기 · 메모 · 마지막에 카테고리(남은 참조가 없는 것만 — FK)</li>
 *   <li>기준: {@code deleted = 'Y'}이고 {@code deleted_at ≤ t0 − 보관 일수}. t0는 한 회에 한 번 정한다. 보관 일수는 설정값(기본 30)</li>
 *   <li>한 번에 {@code batch}행씩 표마다 최대 {@code maxBatches}번, 남으면 다음 회차</li>
 *   <li>로그는 표별 지운 개수만 (회원 번호·내용 없음)</li>
 * </ul>
 * 운영 DB의 기존 지운 행 정리는 별도 승인이라(D-125) prd는 환경 변수로 켜기 전까지 꺼져 있고, local도 꺼 둔다 (대표님 로컬 데이터).
 * 테스트 서버(tst)는 지우지 않고 검출만 한다 — 대상 개수를 로그·지표로만 (대표님 2026-11-27).
 */
@DisplayName("D-125 지운 작성 데이터 30일 뒤 완전 삭제")
class DeletedContentRetentionAcceptanceTest extends AcceptanceTest {

    private static final String SECRET = "Zq7xPurgeMarker42";

    @Autowired
    private DeletedContentPurger purger;

    @Autowired
    private MeterRegistry meters;

    private int diaryDay;

    @Test
    @DisplayName("30일이 지난 행은 6종 모두 완전 삭제되고(일정의 상세 행 포함), 29일째 행·삭제 안 된 행·다른 회원 행은 그대로다")
    void boundary() throws Exception {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        Map<String, Long> old = deletedSet(t0.minusDays(30));                          // 딱 30일 → 지움
        Map<String, Long> older = deletedSet(t0.minusDays(400));                       // 오래된 것 → 지움
        Map<String, Long> recent = deletedSet(t0.minusDays(30).plusSeconds(1));        // 29일 23:59:59 → 남음
        Map<String, Long> active = activeSet();                                        // 삭제 안 됨 → 남음
        long lee = createUser("lee@test.local");
        long leeMemo = idOf(mvc.perform(post("/api/v1/memos").with(as(lee)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"이씨 메모\"}")).andExpect(status().isCreated()));
        long leeDeleted = idOf(mvc.perform(post("/api/v1/memos").with(as(lee)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"이씨 지운 메모\"}")).andExpect(status().isCreated()));
        mvc.perform(delete("/api/v1/memos/" + leeDeleted).with(as(lee))).andExpect(status().isNoContent());
        jdbc.update("UPDATE memos SET deleted_at = ? WHERE memo_id = ?", t0.minusDays(3), leeDeleted);

        assertThat(purger.purge()).as("지운 항목 수 (일정의 상세 행은 일정에 포함)").isEqualTo(2 * 6);

        for (Map<String, Long> gone : List.of(old, older)) {
            gone.forEach((table, id) -> assertThat(exists(table, id)).as(table + " " + id).isFalse());
        }
        for (Map<String, Long> kept : List.of(recent, active)) {
            kept.forEach((table, id) -> assertThat(exists(table, id)).as(table + " " + id).isTrue());
        }
        assertThat(exists("memos", leeMemo)).isTrue();
        assertThat(exists("memos", leeDeleted)).isTrue();
        assertThat(purger.purge()).as("다시 돌려도 더 지울 것 없음").isZero();
    }

    @Test
    @DisplayName("보관 일수는 설정값이고 기본 30일, 매일 한 번 돈다(04:40 KST) — prd는 환경 변수로 켜기 전까지 꺼짐(기존 운영 행 정리 별도 승인), local도 꺼짐")
    void settings() throws Exception {
        assertThat(purger.retentionDays()).isEqualTo(30);
        Method job = DeletedContentPurger.class.getMethod("purge");
        Scheduled scheduled = job.getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 40 4 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");

        assertThat(profileValue("application.yml", null, "app.retention.deleted-content.days")).isEqualTo(30);
        assertThat(profileValue("application.yml", null, "app.retention.deleted-content.enabled")).isEqualTo(true);
        assertThat(profileValue("profile/application-prd.yml", "prd", "app.retention.deleted-content.enabled"))
            .isEqualTo("${JP_DELETED_CONTENT_PURGE_ENABLED:false}");
        assertThat(profileValue("profile/application-local.yml", "local", "app.retention.deleted-content.enabled")).isEqualTo(false);
        assertThat(profileValue("application.yml", null, "app.retention.deleted-content.dry-run")).isEqualTo(false);
        assertThat(profileValue("profile/application-tst.yml", "tst", "app.retention.deleted-content.dry-run")).isEqualTo(true);
        assertThat(profileValue("profile/application-tst.yml", "tst", "app.retention.deleted-content.enabled")).isNull();
    }

    @Test
    @DisplayName("꺼져 있으면 아무것도 지우지 않는다")
    void disabledDoesNothing() throws Exception {
        deletedSet(LocalDateTime.now(clock).minusDays(100));
        DeletedContentPurger off = new DeletedContentPurger(jdbc, clock, meters, transactionManager, false, false, 30);
        assertThat(off.purge()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memos WHERE deleted = 'Y'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("검출만(tst): 30일 지난 대상 개수를 로그·지표로 내고 아무것도 지우지 않는다 — 참조가 남은 카테고리는 대상에서 빠진다")
    void dryRunOnlyCounts() throws Exception {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        Map<String, Long> old = deletedSet(t0.minusDays(31));
        Map<String, Long> recent = deletedSet(t0.minusDays(3));
        DeletedContentPurger dryRun = new DeletedContentPurger(jdbc, clock, meters, transactionManager, true, true, 30);
        Logger logger = (Logger) LoggerFactory.getLogger(DeletedContentPurger.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertThat(dryRun.purge()).as("지운 수").isZero();
        } finally {
            logger.detachAppender(logs);
        }
        for (Map<String, Long> kept : List.of(old, recent)) {
            kept.forEach((table, id) -> assertThat(exists(table, id)).as(table + " " + id).isTrue());
        }
        assertThat(dryRun.lastCandidates()).containsExactly(
            Map.entry("calendars", 1), Map.entry("todos", 1), Map.entry("ddays", 1),
            Map.entry("diaries", 1), Map.entry("memos", 1), Map.entry("categories", 1));
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
            .anySatisfy(line -> assertThat(line).contains("검출만").contains("memos=1").contains("categories=1"));
        assertThat(counter("memos")).as("지운 수 지표는 늘지 않음").isZero();
    }

    @Test
    @DisplayName("한 회 상한을 넘으면 나눠 지우고 남은 것은 다음 회차에 — 남은 행 수·지운 수·마지막 성공을 지표로 낸다")
    void batches() throws Exception {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        for (int i = 0; i < 5; i++) {
            long memo = idOf(postJson("/api/v1/memos", "{\"title\":\"m" + i + "\"}"));
            deleteJson("/api/v1/memos/" + memo).andExpect(status().isNoContent());
        }
        jdbc.update("UPDATE memos SET deleted_at = ?", t0.minusDays(31));
        double before = counter("memos");

        assertThat(purger.purge(2, 2)).isEqualTo(4);
        assertThat(gauge("jp.retention.overdue.rows", "memos")).isEqualTo(1);
        assertThat(gauge("jp.retention.oldest.overdue.age.seconds", "memos")).isEqualTo(Duration.ofDays(1).toSeconds());
        assertThat(purger.purge(2, 2)).isEqualTo(1);
        assertThat(gauge("jp.retention.overdue.rows", "memos")).isZero();
        assertThat(counter("memos") - before).isEqualTo(5);
        assertThat(meters.find("jp.retention.last.success").tag("job", "deleted-content").gauge().value()).isPositive();
    }

    @Test
    @DisplayName("카테고리는 아직 참조하는 행이 있으면 건너뛰고(오류 없음), 그 행이 지워진 뒤 회차에 지운다")
    void categoryStillReferencedIsSkipped() throws Exception {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        long category = createCategory("옛 카테고리");
        deleteJson("/api/v1/categories/" + category).andExpect(status().isNoContent());
        // 지운 카테고리를 여전히 가리키는 Todo (정상 흐름에서는 기본 카테고리로 옮겨지지만, 남아 있어도 작업이 멈추면 안 됨)
        long todo = createDayTodo("남은 Todo", TODAY);
        jdbc.update("UPDATE todos SET category_id = ? WHERE todo_id = ?", category, todo);
        jdbc.update("UPDATE categories SET deleted_at = ? WHERE category_id = ?", t0.minusDays(40), category);

        assertThat(purger.purge()).isZero();
        assertThat(exists("categories", category)).isTrue();

        deleteJson("/api/v1/todos/" + todo).andExpect(status().isNoContent());
        jdbc.update("UPDATE todos SET deleted_at = ? WHERE todo_id = ?", t0.minusDays(40), todo);
        assertThat(purger.purge()).isEqualTo(2);
        assertThat(exists("categories", category)).isFalse();
    }

    @Test
    @DisplayName("로그에는 표별 지운 개수만 — 회원 번호·내용은 남지 않는다")
    void logsOnlyCounts() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(DeletedContentPurger.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        Level level = logger.getLevel();
        logger.setLevel(Level.TRACE);
        try {
            long memo = idOf(postJson("/api/v1/memos", "{\"title\":\"" + SECRET + "\",\"content\":\"" + SECRET + "\"}"));
            deleteJson("/api/v1/memos/" + memo).andExpect(status().isNoContent());
            jdbc.update("UPDATE memos SET deleted_at = ? WHERE memo_id = ?", LocalDateTime.now(clock).minusDays(31), memo);
            assertThat(purger.purge()).isEqualTo(1);
        } finally {
            logger.detachAppender(logs);
            logger.setLevel(level);
        }
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
            .allSatisfy(line -> assertThat(line).doesNotContain(SECRET).doesNotContainPattern("user|회원 ?\\d"))
            .anySatisfy(line -> assertThat(line).contains("memos=1"));
    }

    // ------------------------------------------------------------------ 준비

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** 6종을 하나씩 만들어 API로 지우고, 지운 시각을 정한다. 반환: 표 → id (calendar_details는 calendars와 같은 id) */
    private Map<String, Long> deletedSet(LocalDateTime deletedAt) throws Exception {
        Map<String, Long> set = activeSet();
        deleteJson("/api/v1/schedules/" + set.get("calendars")).andExpect(status().isNoContent());
        deleteJson("/api/v1/todos/" + set.get("todos")).andExpect(status().isNoContent());
        deleteJson("/api/v1/ddays/" + set.get("ddays")).andExpect(status().isNoContent());
        deleteJson("/api/v1/diaries/" + diaryDate(set.get("diaries"))).andExpect(status().isNoContent());
        deleteJson("/api/v1/memos/" + set.get("memos")).andExpect(status().isNoContent());
        deleteJson("/api/v1/categories/" + set.get("categories")).andExpect(status().isNoContent());
        set.forEach((table, id) -> jdbc.update("UPDATE " + table + " SET deleted_at = ? WHERE " + idColumn(table) + " = ?", deletedAt, id));
        return set;
    }

    private Map<String, Long> activeSet() throws Exception {
        Map<String, Long> set = new LinkedHashMap<>();
        long category = createCategory("카테고리" + diaryDay);
        set.put("categories", category);
        long schedule = createSchedule("일정", "2026-09-25T10:00:00", "2026-09-25T11:00:00", null);
        set.put("calendars", schedule);
        set.put("calendar_details", schedule);
        set.put("todos", createDayTodo("할 일", TODAY));
        set.put("ddays", idOf(postJson("/api/v1/ddays", "{\"title\":\"시험\",\"targetDate\":\"2026-09-26\",\"countType\":\"COUNTDOWN\","
            + "\"display\":{\"lastDays\":{\"enabled\":true,\"days\":7}}}").andExpect(status().isCreated())));
        String date = LocalDate.parse(TODAY).minusDays(diaryDay++).toString();
        putJson("/api/v1/diaries/" + date, "{\"content\":\"일기\"}").andExpect(status().isCreated());
        set.put("diaries", jdbc.queryForObject("SELECT diary_id FROM diaries WHERE user_id = ? AND diary_date = ? AND deleted = 'N'",
            Long.class, ADMIN_ID, date));
        set.put("memos", idOf(postJson("/api/v1/memos", "{\"title\":\"메모\"}").andExpect(status().isCreated())));
        return set;
    }

    private String diaryDate(long diaryId) {
        return jdbc.queryForObject("SELECT diary_date FROM diaries WHERE diary_id = ?", String.class, diaryId);
    }

    private static String idColumn(String table) {
        return switch (table) {
            case "calendars", "calendar_details" -> "calendar_id";
            case "todos" -> "todo_id";
            case "ddays" -> "dday_id";
            case "diaries" -> "diary_id";
            case "memos" -> "memo_id";
            case "categories" -> "category_id";
            default -> throw new IllegalArgumentException(table);
        };
    }

    private boolean exists(String table, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + idColumn(table) + " = ?", Integer.class, id) > 0;
    }

    private double counter(String table) {
        var c = meters.find("jp.retention.deleted").tag("table", table).counter();
        return c == null ? 0 : c.count();
    }

    private double gauge(String name, String table) {
        return meters.find(name).tag("table", table).gauge().value();
    }

    private static Object profileValue(String file, String profile, String key) throws Exception {
        for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new FileSystemResource("src/main/resources/" + file))) {
            Object activeOn = source.getProperty("spring.config.activate.on-profile");
            if ((profile == null ? activeOn == null : profile.equals(activeOn)) && source.containsProperty(key)) {
                return source.getProperty(key);
            }
        }
        return null;
    }

}
