package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * US-41 서비스가 보안 기본 설정(헤더·CORS·CSRF·요청 제한·오류 응답·비밀값·공급망)을 갖춘다 (SEC-04~13·15).
 * 설계: 08-api-design.md 13-7·13-8, 07 11-6·11-8.
 * <p>
 * AC2: 요청 제한 — 로그인 IP는 첫 시도부터 1분 창, Redis 키의 대상(IP·이메일·회원)은 비밀 키({@code app.ratelimit.key}) HMAC-SHA256.
 * AC3: 프록시(Caddy/nginx) 뒤에서도 진짜 접속 IP로 요청 제한·감사 기록·기기 목록을 남긴다 — 믿는 프록시 주소(설정 {@code app.network.trusted-proxies},
 *      테스트는 10.0.0.0/8)에서 온 X-Forwarded-For만, 오른쪽부터 믿는 프록시를 건너뛴 첫 주소.
 * <p>
 * 운영의 HTTPS·HSTS·프록시 설정 자체는 배포(Q-028·Q-031) 때 확인.
 */
@DisplayName("US-41 보안 기본 설정")
class US41SecurityAcceptanceTest extends BrowserAcceptanceTest {

    private static final String KIM = "kim@test.local";
    private static final String PW = "member-password-123";
    private static final String PROXY = "10.0.0.5";

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private PrometheusMeterRegistry prometheus;

    private long kim;

    @BeforeEach
    void setUp() {
        kim = createUser(KIM);
        setPassword(kim, PW);
    }

    // ------------------------------------------------------------------ AC3 진짜 IP

    @Test
    @DisplayName("AC3: 믿는 프록시를 거친 요청은 X-Forwarded-For의 진짜 IP로 감사 기록·로그인 기기 목록에 남는다")
    void realIpBehindProxy() throws Exception {
        Browser b = browser(PROXY).via("198.51.100.7").withCsrf();
        b.login(KIM, PW).andExpect(status().isOk());
        assertThat(loginIp()).isEqualTo("198.51.100.7");
        b.get("/api/v1/me/sessions").andExpect(status().isOk()).andExpect(jsonPath("$[0].ipPrefix").value("198.51.*.*"));
    }

    @Test
    @DisplayName("AC3: 클라이언트가 X-Forwarded-For 앞쪽에 끼워 넣은 값은 무시한다 — 오른쪽부터 믿는 프록시를 건너뛴 첫 주소")
    void ignoresSpoofedPrefix() throws Exception {
        browser(PROXY).via("1.2.3.4, 198.51.100.8").withCsrf().login(KIM, PW).andExpect(status().isOk());
        assertThat(loginIp()).isEqualTo("198.51.100.8");

        clearAudit();
        browser(PROXY).via("198.51.100.9, 10.0.0.6").withCsrf().login(KIM, PW).andExpect(status().isOk());
        assertThat(loginIp()).isEqualTo("198.51.100.9");
    }

    @Test
    @DisplayName("AC3: 믿지 않는 주소에서 온 X-Forwarded-For는 무시하고 연결 주소를 쓴다. 믿는 프록시의 X-Forwarded-For가 IP가 아니면 프록시 주소")
    void untrustedForwardedForIgnored() throws Exception {
        browser("203.0.113.9").via("1.2.3.4").withCsrf().login(KIM, PW).andExpect(status().isOk());
        assertThat(loginIp()).isEqualTo("203.0.113.9");

        clearAudit();
        browser(PROXY).via("not-an-ip").withCsrf().login(KIM, PW).andExpect(status().isOk());
        assertThat(loginIp()).isEqualTo(PROXY);
    }

    @Test
    @DisplayName("AC3: 로그인 IP 제한(login-ip)은 진짜 IP마다 센다 — 같은 프록시 뒤의 다른 사람은 막히지 않는다")
    void loginLimitPerRealIp() throws Exception {
        Browser a = browser(PROXY).via("198.51.100.20").withCsrf();
        for (int i = 0; i < 20; i++) {
            a.login("nobody" + i + "@test.local", "any-password-1234").andExpect(status().isUnauthorized());
        }
        a.login(KIM, PW).andExpect(status().isTooManyRequests());
        browser(PROXY).via("198.51.100.21").withCsrf().login(KIM, PW).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC2: 로그인 IP 제한은 첫 시도부터 1분 창이다 (시계의 '분'이 바뀌어도 이어서 센다) — 카운터 하나, 남은 시간 60초 이하")
    void loginIpWindowStartsAtFirstTry() throws Exception {
        browser("198.51.100.30").withCsrf().login("nobody@test.local", "any-password-1234").andExpect(status().isUnauthorized());
        Set<String> keys = redis.keys("jp:rl:login-ip:*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(redis.opsForValue().get(key)).isEqualTo("1");
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("AC2: 요청 제한 Redis 키의 대상은 비밀 키 HMAC-SHA256 — IP·이메일의 SHA-256이 보이지 않는다")
    void rateLimitKeysAreKeyedHashes() throws Exception {
        browser("198.51.100.31").withCsrf().login("nobody@test.local", "any-password-1234").andExpect(status().isUnauthorized());
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("198.51.100.31".getBytes(StandardCharsets.UTF_8)), 0, 16);
        String failSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("nobody@test.local".getBytes(StandardCharsets.UTF_8)), 0, 16);
        Set<String> keys = redis.keys("jp:rl:*");
        assertThat(keys).isNotEmpty().noneMatch(k -> k.endsWith(sha) || k.endsWith(failSha));
        assertThat(redis.keys("jp:rl:login-ip:*")).containsExactly("jp:rl:login-ip:" + hmac("198.51.100.31"));
    }

    // ------------------------------------------------------------------ 보안 지표 (08 13-8)

    @Test
    @DisplayName("지표: 로그인 jp_auth_login_total{result=SUCCESS|FAILED|SUSPENDED|LOCKED, method=PASSWORD} — 태그에 IP·이메일·회원 번호 없음")
    void loginMetrics() throws Exception {
        double success = count("jp.auth.login", "result", "SUCCESS", "method", "PASSWORD");
        double failed = count("jp.auth.login", "result", "FAILED", "method", "PASSWORD");
        double suspended = count("jp.auth.login", "result", "SUSPENDED", "method", "PASSWORD");
        double locked = count("jp.auth.login", "result", "LOCKED", "method", "PASSWORD");

        Browser b = browser("198.51.100.40").withCsrf();
        b.login(KIM, PW).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            b.login(KIM, "wrong-password-123").andExpect(status().isUnauthorized());
        }
        b.login(KIM, PW).andExpect(status().isTooManyRequests());
        long lee = createUser("lee@test.local");
        setPassword(lee, PW);
        jdbc.update("UPDATE users SET suspended_at = NOW() WHERE user_id = ?", lee);
        b.login("lee@test.local", PW).andExpect(status().isForbidden());

        assertThat(count("jp.auth.login", "result", "SUCCESS", "method", "PASSWORD") - success).isEqualTo(1);
        assertThat(count("jp.auth.login", "result", "FAILED", "method", "PASSWORD") - failed).isEqualTo(5);
        assertThat(count("jp.auth.login", "result", "LOCKED", "method", "PASSWORD") - locked).isEqualTo(1);
        assertThat(count("jp.auth.login", "result", "SUSPENDED", "method", "PASSWORD") - suspended).isEqualTo(1);
        assertThat(meters.find("jp.auth.login").meters()).allSatisfy(m ->
            assertThat(m.getId().getTags()).extracting(Tag::getKey).containsOnly("result", "method"));
    }

    @Test
    @DisplayName("지표: 요청 제한에 걸리면 jp_ratelimit_rejected_total{rule} — 감사 기록의 rule 값과 같다 (메일만 안 보내는 signup-resend도)")
    void rateLimitMetrics() throws Exception {
        double loginIp = count("jp.ratelimit.rejected", "rule", "login-ip");
        Browser b = browser("198.51.100.41").withCsrf();
        for (int i = 0; i < 21; i++) {
            b.login("nobody" + i + "@test.local", "any-password-1234");
        }
        assertThat(count("jp.ratelimit.rejected", "rule", "login-ip") - loginIp).isEqualTo(1);

        double resend = count("jp.ratelimit.rejected", "rule", "signup-resend");
        Browser s = browser("198.51.100.42").withCsrf();
        s.postJson("/api/v1/auth/signup", "{\"email\":\"new@test.local\",\"password\":\"pending-password-123\",\"name\":\"새\",\"ageOver14\":true}")
            .andExpect(status().isAccepted());
        for (int i = 0; i < 11; i++) {
            clock.advance(java.time.Duration.ofMinutes(2));
            browser("198.51.100." + (100 + i)).withCsrf()
                .postJson("/api/v1/auth/signup/resend", "{\"email\":\"new@test.local\"}").andExpect(status().isAccepted());
        }
        assertThat(count("jp.ratelimit.rejected", "rule", "signup-resend") - resend).isEqualTo(1);
    }

    @Test
    @DisplayName("지표: 메일 jp_auth_mail_total{type=SIGNUP|RESET|EMAIL_CHANGE|NOTICE, result=SENT|FAILED} — 실패해도 응답은 그대로 202")
    void mailMetrics() throws Exception {
        double sent = count("jp.auth.mail", "type", "RESET", "result", "SENT");
        double failed = count("jp.auth.mail", "type", "RESET", "result", "FAILED");
        Browser b = browser("198.51.100.43").withCsrf();
        b.postJson("/api/v1/auth/password/forgot", "{\"email\":\"" + KIM + "\"}").andExpect(status().isAccepted());
        mailer.failNext();
        b.postJson("/api/v1/auth/password/forgot", "{\"email\":\"" + KIM + "\"}").andExpect(status().isAccepted());
        assertThat(count("jp.auth.mail", "type", "RESET", "result", "SENT") - sent).isEqualTo(1);
        assertThat(count("jp.auth.mail", "type", "RESET", "result", "FAILED") - failed).isEqualTo(1);
    }

    @Test
    @DisplayName("지표는 Prometheus 형식으로 내보낸다 (이름 jp_auth_login_total …). Actuator는 앱 주소(/api와 같은 포트)에서 열리지 않는다 — 관리 포트 따로")
    void prometheusOnManagementPortOnly() throws Exception {
        browser("198.51.100.44").withCsrf().login(KIM, PW).andExpect(status().isOk());
        assertThat(prometheus.scrape()).contains("jp_auth_login_total{method=\"PASSWORD\",result=\"SUCCESS\"}");
        browser("198.51.100.45").get("/actuator/prometheus").andExpect(status().isNotFound());
        browser("198.51.100.45").get("/actuator/health").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 도우미

    private double count(String name, String... tags) {
        Counter counter = meters.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private String loginIp() {
        return jdbc.queryForObject("SELECT ip FROM audit_logs WHERE user_id = ? AND event = 'LOGIN' ORDER BY audit_id DESC LIMIT 1", String.class, kim);
    }

    /** 테스트 설정의 키(app.ratelimit.key)로 계산한 HMAC-SHA256 앞 16바이트 */
    private static String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode("dGVzdC1vbmx5LXJhdGUtbGltaXQta2V5LTMyYnl0ZXMh"), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)), 0, 16);
    }

    private void clearAudit() {
        jdbc.update("DELETE FROM audit_logs");
    }
}
