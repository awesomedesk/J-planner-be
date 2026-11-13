package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.api.v1.auth.AuthTokenCleaner;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

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
