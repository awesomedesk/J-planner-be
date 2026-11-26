package com.awesomedesk.j_planner.api.v1.user;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사용자가 지운 작성 데이터를 보관 기간(기본 30일) 뒤 완전 삭제한다 — D-125 (대표님 #410 'B', PO #425).
 * <ul>
 *   <li>대상: {@code deleted = 'Y'}이고 {@code deleted_at ≤ t0 − days}인 행. t0는 한 회에 한 번 정한다</li>
 *   <li>순서: 일정(상세 행 {@code calendar_details} 먼저) → Todo → D-Day → 일기 → 메모 → 카테고리.
 *       카테고리는 FK라 아직 가리키는 일정·Todo가 있으면 건너뛰고 다음 회차에 (정상 흐름에서는 삭제 때 기본 카테고리로 옮겨져 없음)</li>
 *   <li>한 번에 {@code batch}행씩 표마다 최대 {@code maxBatches}번 — 남으면 다음 회차. 한 묶음은 한 트랜잭션</li>
 *   <li>로그는 표별 지운 개수만 (회원 번호·내용 없음). 지표는 {@link com.awesomedesk.j_planner.api.v1.auth.AuthTokenCleaner}와 같은 이름 ({@code table}·{@code job=deleted-content})</li>
 *   <li>{@code app.retention.deleted-content.enabled=false}면 아무것도 안 함 — prd는 기존 운영 행 정리 승인 뒤 환경 변수로 켠다 (D-125)</li>
 * </ul>
 */
@Slf4j
@Component
public class DeletedContentPurger {

    private static final String JOB = "deleted-content";
    static final int BATCH = 500;
    static final int MAX_BATCHES = 100;

    /** 지우는 순서. 값 = 기본 키 열 */
    private static final Map<String, String> TABLES = orderedTables();

    private static final String CATEGORY_UNREFERENCED = " AND NOT EXISTS (SELECT 1 FROM calendars c WHERE c.category_id = t.category_id)"
        + " AND NOT EXISTS (SELECT 1 FROM todos d WHERE d.category_id = t.category_id)";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final boolean enabled;
    private final int days;
    private final MeterRegistry registry;
    private final Counter failures;
    private final Map<String, AtomicLong> overdueRows = new LinkedHashMap<>();
    private final Map<String, AtomicLong> oldestOverdueSeconds = new LinkedHashMap<>();
    private final AtomicLong lastSuccess;

    public DeletedContentPurger(JdbcTemplate jdbc, Clock clock, MeterRegistry registry, PlatformTransactionManager transactionManager,
                                @Value("${app.retention.deleted-content.enabled:true}") boolean enabled,
                                @Value("${app.retention.deleted-content.days:30}") int days) {
        if (days < 1) {
            throw new IllegalArgumentException("app.retention.deleted-content.days는 1 이상: " + days);
        }
        this.jdbc = jdbc;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.days = days;
        this.registry = registry;
        this.failures = registry.counter("jp.retention.failures", "job", JOB);
        for (String table : TABLES.keySet()) {
            overdueRows.put(table, registry.gauge("jp.retention.overdue.rows", Tags.of("table", table), new AtomicLong()));
            oldestOverdueSeconds.put(table, registry.gauge("jp.retention.oldest.overdue.age.seconds", Tags.of("table", table), new AtomicLong()));
        }
        this.lastSuccess = registry.gauge("jp.retention.last.success", Tags.of("job", JOB), new AtomicLong());
    }

    public int retentionDays() {
        return days;
    }

    /** 매일 04:40 KST (휴면 처리 04:10 뒤) */
    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    public int purge() {
        return purge(BATCH, MAX_BATCHES);
    }

    /** @return 이번 회차에 지운 항목 수 (일정의 상세 행은 일정에 포함해 세지 않음) */
    public int purge(int batch, int maxBatches) {
        if (!enabled) {
            return 0;
        }
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        LocalDateTime cutoff = t0.minusDays(days);
        Map<String, Integer> counts = new LinkedHashMap<>();
        boolean failed = false;
        for (Map.Entry<String, String> entry : TABLES.entrySet()) {
            String table = entry.getKey();
            int n = 0;
            try {
                n = purgeTable(table, entry.getValue(), cutoff, batch, maxBatches);
                updateOverdue(table, cutoff);
            } catch (RuntimeException e) {
                failed = true;
                failures.increment();
                log.error("지운 작성 데이터 정리 실패 — 다음 회차에 다시: {} (이번에 {}건 지움)", table, n, e);
            }
            counts.put(table, n);
            if (n > 0) {
                registry.counter("jp.retention.deleted", "table", table).increment(n);
            }
        }
        if (!failed) {
            lastSuccess.set(clock.instant().getEpochSecond());
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total > 0 || failed) {
            StringJoiner line = new StringJoiner(", ");
            counts.forEach((table, n) -> line.add(table + "=" + n));
            log.info("지운 작성 데이터 완전 삭제 (보관 {}일): {}", days, line);
        }
        return total;
    }

    private int purgeTable(String table, String idColumn, LocalDateTime cutoff, int batch, int maxBatches) {
        String condition = "t.deleted = 'Y' AND t.deleted_at <= ?" + ("categories".equals(table) ? CATEGORY_UNREFERENCED : "");
        int total = 0;
        for (int i = 0; i < maxBatches; i++) {
            List<Long> ids = jdbc.queryForList("SELECT t." + idColumn + " FROM " + table + " t WHERE " + condition
                + " ORDER BY t.deleted_at, t." + idColumn + " LIMIT ?", Long.class, cutoff, batch);
            if (ids.isEmpty()) {
                break;
            }
            Integer n = transaction.execute(status -> deleteIds(table, idColumn, condition, cutoff, ids));
            total += n == null ? 0 : n;
            if (ids.size() < batch) {
                break;
            }
        }
        return total;
    }

    private int deleteIds(String table, String idColumn, String condition, LocalDateTime cutoff, List<Long> ids) {
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids);
        if ("calendars".equals(table)) {
            // 상세 행은 일정이 지워질 때만 — 같은 조건을 다시 확인 (그 사이 되살린 일정은 건드리지 않음)
            List<Object> detailArgs = new ArrayList<>(args);
            detailArgs.add(cutoff);
            jdbc.update("DELETE FROM calendar_details WHERE calendar_id IN (SELECT t.calendar_id FROM calendars t WHERE t.calendar_id IN (" + in
                + ") AND t.deleted = 'Y' AND t.deleted_at <= ?)", detailArgs.toArray());
        }
        args.add(cutoff);
        return jdbc.update("DELETE t FROM " + table + " t WHERE t." + idColumn + " IN (" + in + ") AND " + condition, args.toArray());
    }

    private void updateOverdue(String table, LocalDateTime cutoff) {
        Map<String, Object> left = jdbc.queryForMap(
            "SELECT COUNT(*) AS n, MIN(deleted_at) AS oldest FROM " + table + " WHERE deleted = 'Y' AND deleted_at <= ?", cutoff);
        overdueRows.get(table).set(((Number) left.get("n")).longValue());
        LocalDateTime oldest = (LocalDateTime) left.get("oldest");
        oldestOverdueSeconds.get(table).set(oldest == null ? 0 : Duration.between(oldest, cutoff).toSeconds());
    }

    private static Map<String, String> orderedTables() {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("calendars", "calendar_id");
        tables.put("todos", "todo_id");
        tables.put("ddays", "dday_id");
        tables.put("diaries", "diary_id");
        tables.put("memos", "memo_id");
        tables.put("categories", "category_id");
        return Collections.unmodifiableMap(tables);
    }
}
