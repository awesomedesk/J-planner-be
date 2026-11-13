package com.awesomedesk.j_planner.api.v1.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 메일 링크 토큰(`auth_tokens`)을 매시 지운다 — 사용·만료 뒤 추가 보관 유예 0일 (대표님 #353, PO #333).
 * 쓰거나 새로 요청한 토큰은 그 자리(같은 트랜잭션)에서 지우고, 여기서는 <b>쓰지 않고 만료된</b> 것만 정리한다.
 * <ul>
 *   <li>한 회의 기준 시각 {@code t0}를 시작 때 한 번 정하고, {@code expires_at <= t0}만 지운다 (쓸 수 있는 조건은 {@code expires_at > now}라 유효 토큰은 못 지움)</li>
 *   <li>한 번에 {@code batch}행씩 최대 {@code maxBatches}번 — 남으면 다음 회차가 이어 한다. '1시간 안 삭제'는 목표이지 보장이 아니어서 잔량을 지표로 감시</li>
 *   <li>지표: {@code jp_retention_deleted_total{table}}, {@code jp_retention_overdue_rows{table}}, {@code jp_retention_oldest_overdue_age_seconds{table}},
 *       {@code jp_retention_last_success{job}}(epoch 초), {@code jp_retention_failures_total{job}}</li>
 * </ul>
 */
@Slf4j
@Component
public class AuthTokenCleaner {

    private static final String TABLE = "auth_tokens";
    private static final String JOB = "auth-tokens";
    static final int BATCH = 1000;
    static final int MAX_BATCHES = 50;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Counter deleted;
    private final Counter failures;
    private final AtomicLong overdueRows = new AtomicLong();
    private final AtomicLong oldestOverdueSeconds = new AtomicLong();
    private final AtomicLong lastSuccess = new AtomicLong();

    public AuthTokenCleaner(JdbcTemplate jdbc, Clock clock, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.deleted = registry.counter("jp.retention.deleted", "table", TABLE);
        this.failures = registry.counter("jp.retention.failures", "job", JOB);
        registry.gauge("jp.retention.overdue.rows", Tags.of("table", TABLE), overdueRows);
        registry.gauge("jp.retention.oldest.overdue.age.seconds", Tags.of("table", TABLE), oldestOverdueSeconds);
        registry.gauge("jp.retention.last.success", Tags.of("job", JOB), lastSuccess);
    }

    /** 매시 17분 (정각 몰림 피함, 임시 가입 정리는 7분) */
    @Scheduled(cron = "0 17 * * * *", zone = "Asia/Seoul")
    public int deleteExpired() {
        return deleteExpired(BATCH, MAX_BATCHES);
    }

    /** @return 이번 회차에 지운 행 수 */
    public int deleteExpired(int batch, int maxBatches) {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        int total = 0;
        try {
            for (int i = 0; i < maxBatches; i++) {
                int n = jdbc.update("DELETE FROM auth_tokens WHERE expires_at <= ? ORDER BY expires_at LIMIT ?", t0, batch);
                total += n;
                if (n < batch) {
                    break;
                }
            }
            Map<String, Object> left = jdbc.queryForMap("SELECT COUNT(*) AS n, MIN(expires_at) AS oldest FROM auth_tokens WHERE expires_at <= ?", t0);
            overdueRows.set(((Number) left.get("n")).longValue());
            LocalDateTime oldest = (LocalDateTime) left.get("oldest");
            oldestOverdueSeconds.set(oldest == null ? 0 : Duration.between(oldest, t0).toSeconds());
            lastSuccess.set(clock.instant().getEpochSecond());
        } catch (RuntimeException e) {
            failures.increment();
            log.error("만료 메일 링크 정리 실패 — 다음 회차에 다시: 이번에 {}행 지움", total, e);
        } finally {
            deleted.increment(total);
        }
        if (total > 0) {
            log.info("만료 메일 링크 {}행 지움 (남은 만료 {}행)", total, overdueRows.get());
        }
        return total;
    }
}
