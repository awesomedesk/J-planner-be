package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.api.v1.auth.AuthTokenCleaner;
import com.awesomedesk.j_planner.support.TestClock;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 메일 링크 토큰(`auth_tokens`) 보관 — 대표님 #353(Q-035): 사용·만료 뒤 추가 보관 유예 0일. 기술 계획은 PO #333.
 * <ul>
 *   <li>쓰거나(재설정·새 이메일 인증) 새로 요청해 무효가 되는 토큰은 <b>같은 트랜잭션에서 삭제</b></li>
 *   <li>쓰지 않고 만료된 토큰은 매시 정리 작업이 삭제 (고정 기준 시각, 한 번에 상한, 남으면 다음 회차)</li>
 * </ul>
 * 지운 뒤 같은 링크는 지금처럼 400 TOKEN_INVALID (없음·만료·사용됨을 구분하지 않음).
 */
@DisplayName("메일 링크 토큰 보관 (유예 0일, #353)")
class AuthTokenRetentionAcceptanceTest extends BrowserAcceptanceTest {

    private static final String KIM = "kim@test.local";
    private static final String PW = "member-password-123";

    @Autowired
    private AuthTokenCleaner cleaner;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private TransactionTemplate transaction;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ExecutorService executor2 = Executors.newSingleThreadExecutor();
    private Future<Integer> pending;

    private long kim;

    @BeforeEach
    void setUp() {
        kim = createUser(KIM);
        setPassword(kim, PW);
    }

    @Test
    @DisplayName("비밀번호 재설정: 새로 요청하면 이전 토큰은 남지 않고, 재설정에 쓰면 그 토큰도 같은 트랜잭션에서 지워진다 — 같은 링크는 400")
    void passwordResetTokensDeleted() throws Exception {
        Browser b = browser("198.51.100.70").withCsrf();
        b.postJson("/api/v1/auth/password/forgot", "{\"email\":\"" + KIM + "\"}").andExpect(status().isAccepted());
        String first = token(mailer.sentTo(KIM, "RESET").getLast().link());
        b.postJson("/api/v1/auth/password/forgot", "{\"email\":\"" + KIM + "\"}").andExpect(status().isAccepted());
        String second = token(mailer.sentTo(KIM, "RESET").getLast().link());
        assertThat(rows(kim, "PASSWORD_RESET")).isEqualTo(1);

        b.postJson("/api/v1/auth/password/reset", "{\"token\":\"" + first + "\",\"newPassword\":\"brand-new-password-678\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        b.postJson("/api/v1/auth/password/reset", "{\"token\":\"" + second + "\",\"newPassword\":\"brand-new-password-678\"}")
            .andExpect(status().isNoContent());
        assertThat(rows(kim, "PASSWORD_RESET")).isZero();
        b.postJson("/api/v1/auth/password/reset", "{\"token\":\"" + second + "\",\"newPassword\":\"another-password-999\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("이메일 변경: 새로 요청하면 이전 토큰은 남지 않고, 새 주소 인증에 쓰면 그 토큰도 지워진다")
    void emailChangeTokensDeleted() throws Exception {
        Browser b = loggedIn(KIM, PW, "198.51.100.71");
        b.postJson("/api/v1/me/reauth", "{\"password\":\"" + PW + "\"}").andExpect(status().isNoContent());
        b.perform(put("/api/v1/me/email").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"one@test.local\"}")).andExpect(status().isAccepted());
        b.perform(put("/api/v1/me/email").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"two@test.local\"}")).andExpect(status().isAccepted());
        assertThat(rows(kim, "EMAIL_CHANGE")).isEqualTo(1);

        String token = token(mailer.sentTo("two@test.local", "EMAIL_CHANGE").getLast().link());
        browser("198.51.100.72").withCsrf().postJson("/api/v1/me/email/verify", "{\"token\":\"" + token + "\"}").andExpect(status().isNoContent());
        assertThat(rows(kim, "EMAIL_CHANGE")).isZero();
    }

    @Test
    @DisplayName("매시 정리: 고정 기준 시각 t0에 만료된(expires_at ≤ t0) 토큰만 지운다 — 1초 뒤 만료되는 유효 토큰은 남음")
    void hourlyCleanupBoundary() {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        insertToken(kim, "a1", t0.minusSeconds(1));
        insertToken(kim, "a2", t0);
        insertToken(kim, "a3", t0.plusSeconds(1));

        assertThat(cleaner.deleteExpired()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT HEX(token_hash) FROM auth_tokens", String.class)).containsExactly(hash("a3"));
        assertThat(cleaner.deleteExpired()).isZero();
    }

    @Test
    @DisplayName("매시 정리: 한 회 상한을 넘으면 남은 것은 다음 회차에 — 남은 만료 행 수·마지막 성공 시각을 지표로 낸다")
    void hourlyCleanupLimitAndMetrics() {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        for (int i = 0; i < 5; i++) {
            insertToken(kim, "x" + i, t0.minusHours(1));
        }
        double before = meters.find("jp.retention.deleted").tag("table", "auth_tokens").counter().count();

        assertThat(cleaner.deleteExpired(2, 2)).isEqualTo(4);
        assertThat(gauge("jp.retention.overdue.rows")).isEqualTo(1);
        assertThat(gauge("jp.retention.oldest.overdue.age.seconds")).isEqualTo(3600);
        assertThat(cleaner.deleteExpired(2, 2)).isEqualTo(1);
        assertThat(gauge("jp.retention.overdue.rows")).isZero();
        assertThat(meters.find("jp.retention.deleted").tag("table", "auth_tokens").counter().count() - before).isEqualTo(5);
        assertThat(meters.find("jp.retention.last.success").tag("job", "auth-tokens").gauge().value()).isPositive();
    }

    // ------------------------------------------------------------------ 실패·경합 (PO #357)

    @Test
    @DisplayName("경합: 같은 재설정 링크로 동시에 두 번 — 한 번만 204, 다른 하나는 400 TOKEN_INVALID, 남는 행 0")
    void concurrentResetSucceedsOnce() throws Exception {
        Browser b = browser("198.51.100.73").withCsrf();
        b.postJson("/api/v1/auth/password/forgot", "{\"email\":\"" + KIM + "\"}").andExpect(status().isAccepted());
        String token = token(mailer.sentTo(KIM, "RESET").getLast().link());
        Browser c = browser("198.51.100.74").withCsrf();
        String body = "{\"token\":\"" + token + "\",\"newPassword\":\"brand-new-password-678\"}";

        Future<Integer> first;
        Future<Integer> second;
        // 첫 요청이 토큰 행을 잡고 있는 동안 두 번째가 기다리게 — 테스트 트랜잭션이 행을 먼저 잡았다가 놓는다
        first = transaction.execute(status -> {
            lockToken(token);
            Future<Integer> f1 = executor.submit(() -> b.postJson("/api/v1/auth/password/reset", body).andReturn().getResponse().getStatus());
            Future<Integer> f2 = executor.submit(() -> c.postJson("/api/v1/auth/password/reset", body).andReturn().getResponse().getStatus());
            assertBlocked(f1);
            assertBlocked(f2);
            pending = f2;
            return f1;
        });
        second = pending;
        assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(204, 400);
        assertThat(rows(kim, "PASSWORD_RESET")).isZero();
    }

    @Test
    @DisplayName("경합: 재설정이 유효 토큰 행을 잡고 있는 동안 정리 작업은 그 행을 건드리지 않고 끝나고, 재설정은 그대로 204 — 다른 회원의 유효 토큰도 남음")
    void cleanupDuringResetKeepsValidTokens() throws Exception {
        long lee = createUser("lee@test.local");
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        insertToken(lee, "lee-valid", t0.plusMinutes(20));
        insertToken(lee, "lee-expired", t0.minusMinutes(1));
        Browser b = browser("198.51.100.75").withCsrf();
        b.postJson("/api/v1/auth/password/forgot", "{\"email\":\"" + KIM + "\"}").andExpect(status().isAccepted());
        String token = token(mailer.sentTo(KIM, "RESET").getLast().link());

        Future<Integer> reset = transaction.execute(status -> {
            lockToken(token);
            Future<Integer> r = executor.submit(() -> b.postJson("/api/v1/auth/password/reset",
                "{\"token\":\"" + token + "\",\"newPassword\":\"brand-new-password-678\"}").andReturn().getResponse().getStatus());
            assertBlocked(r);
            Future<Integer> cleanup = executor2.submit(() -> cleaner.deleteExpired());
            try {
                assertThat(cleanup.get(10, TimeUnit.SECONDS)).isEqualTo(1); // 만료된 lee-expired만, 잠긴 유효 행은 기다리지도 지우지도 않음
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return r;
        });
        assertThat(reset.get(10, TimeUnit.SECONDS)).isEqualTo(204);
        assertThat(jdbc.queryForList("SELECT HEX(token_hash) FROM auth_tokens", String.class)).containsExactly(hash("lee-valid"));
    }

    @Test
    @DisplayName("실패: 첫 배치 뒤 SQL이 실패하면 실패 지표 +1, 지운 수는 실제로 지운 만큼만, 마지막 성공 시각은 그대로 — 다음 회차가 나머지를 지운다")
    void failureMidRunIsCountedAndRetried() {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        for (int i = 0; i < 5; i++) {
            insertToken(kim, "f" + i, t0.minusHours(1));
        }
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthTokenCleaner failing = new AuthTokenCleaner(new FailingJdbc(jdbc, 2), clock, registry); // 두 번째 DELETE에서 실패
        assertThat(failing.deleteExpired(2, 10)).isEqualTo(2);
        assertThat(registry.counter("jp.retention.failures", "job", "auth-tokens").count()).isEqualTo(1);
        assertThat(registry.counter("jp.retention.deleted", "table", "auth_tokens").count()).isEqualTo(2);
        assertThat(registry.find("jp.retention.last.success").tag("job", "auth-tokens").gauge().value()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_tokens", Integer.class)).isEqualTo(3);

        // 다음 회차 (실제 앱은 정리 작업이 하나라 지표도 하나 — 여기선 새 인스턴스라 새 레지스트리로 본다)
        SimpleMeterRegistry nextRegistry = new SimpleMeterRegistry();
        AuthTokenCleaner next = new AuthTokenCleaner(jdbc, clock, nextRegistry);
        assertThat(next.deleteExpired(2, 10)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_tokens", Integer.class)).isZero();
        assertThat(nextRegistry.find("jp.retention.last.success").tag("job", "auth-tokens").gauge().value()).isPositive();
        assertThat(nextRegistry.counter("jp.retention.failures", "job", "auth-tokens").count()).isZero();
    }

    @Test
    @DisplayName("고정 기준 시각: 회차 도중 시계가 1시간 넘어가도 그 회차는 시작 때의 t0로만 지운다 — t0+30분 만료 토큰은 남음")
    void referenceTimeFixedDuringRun() {
        LocalDateTime t0 = LocalDateTime.now(clock).withNano(0);
        insertToken(kim, "e1", t0.minusMinutes(1));
        insertToken(kim, "e2", t0.minusMinutes(2));
        insertToken(kim, "later", t0.plusMinutes(30));
        AuthTokenCleaner moving = new AuthTokenCleaner(new ClockAdvancingJdbc(jdbc, clock), clock, new SimpleMeterRegistry());
        assertThat(moving.deleteExpired(1, 10)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT HEX(token_hash) FROM auth_tokens", String.class)).containsExactly(hash("later"));
    }

    /** 이 테스트 트랜잭션에서 그 토큰 행을 잠근다 (재설정이 FOR UPDATE로 잡는 것과 같은 잠금) */
    private void lockToken(String token) {
        jdbc.queryForList("SELECT token_id FROM auth_tokens WHERE token_hash = UNHEX(?) FOR UPDATE", Long.class, hashOfToken(token));
    }

    private static String hashOfToken(String token) {
        return HexFormat.of().formatHex(com.awesomedesk.j_planner.api.v1.auth.SignupTokens.hash(token));
    }

    private static void assertBlocked(Future<?> f) {
        try {
            f.get(500, TimeUnit.MILLISECONDS);
            throw new AssertionError("잠금을 기다리지 않고 끝났다");
        } catch (TimeoutException expected) {
            // 기다리는 중
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** n번째 update 호출에서 실패하는 JdbcTemplate (실패 주입) */
    static final class FailingJdbc extends JdbcTemplate {
        private final int failAt;
        private int calls;

        FailingJdbc(JdbcTemplate real, int failAt) {
            super(real.getDataSource());
            this.failAt = failAt;
        }

        @Override
        public int update(String sql, Object... args) {
            if (++calls == failAt) {
                throw new org.springframework.dao.DataAccessResourceFailureException("주입한 실패");
            }
            return super.update(sql, args);
        }
    }

    /** DELETE 한 번마다 시계를 1시간 넘기는 JdbcTemplate — 회차 도중 시각이 바뀌는 상황 */
    static final class ClockAdvancingJdbc extends JdbcTemplate {
        private final TestClock clock;

        ClockAdvancingJdbc(JdbcTemplate real, TestClock clock) {
            super(real.getDataSource());
            this.clock = clock;
        }

        @Override
        public int update(String sql, Object... args) {
            int n = super.update(sql, args);
            clock.advance(java.time.Duration.ofHours(1));
            return n;
        }
    }

    private void insertToken(long userId, String seed, LocalDateTime expiresAt) {
        jdbc.update("INSERT INTO auth_tokens (user_id, purpose, token_hash, expires_at, created_at) VALUES (?, 'PASSWORD_RESET', UNHEX(?), ?, ?)",
            userId, hash(seed), expiresAt, expiresAt.minusMinutes(30));
    }

    private static String hash(String seed) {
        try {
            return HexFormat.of().withUpperCase().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(seed.getBytes()));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private double gauge(String name) {
        Gauge gauge = meters.find(name).tag("table", "auth_tokens").gauge();
        return gauge == null ? -1 : gauge.value();
    }

    private int rows(long userId, String purpose) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM auth_tokens WHERE user_id = ? AND purpose = ?", Integer.class, userId, purpose);
    }

    private static String token(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }
}
