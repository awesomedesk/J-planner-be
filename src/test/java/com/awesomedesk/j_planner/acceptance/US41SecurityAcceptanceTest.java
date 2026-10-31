package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.common.crypto.NotesEncryptor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;

/**
 * US-41 서비스가 보안 기본 설정(헤더·CORS·CSRF·요청 제한·오류 응답·비밀값·공급망)을 갖춘다 (SEC-04~13·15).
 * 설계: 08-api-design.md 13-7·13-8, 07 11-6·11-8.
 * <p>
 * AC1: 보안 헤더 (HTTPS·HSTS는 앞단 프록시가 붙인다 — 배포 때 확인).
 * AC2: 요청 크기 1MB, 일반 API 회원별 분당 300번(api-user). 요청 제한 — 로그인 IP는 첫 시도부터 1분 창, Redis 키의 대상(IP·이메일·회원)은 비밀 키({@code app.ratelimit.key}) HMAC-SHA256.
 * AC4: 일기 내용·메모 제목·내용은 앱에서 AES-256-GCM으로 암호화해 저장 (키 {@code app.crypto.data-key}, 기존 원문은 켤 때 다시 저장).
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
    private NotesEncryptor encryptor;

    @Autowired
    private PrometheusMeterRegistry prometheus;

    private long kim;

    @BeforeEach
    void setUp() {
        kim = createUser(KIM);
        setPassword(kim, PW);
    }

    // ------------------------------------------------------------------ AC1 보안 헤더

    @Test
    @DisplayName("AC1: API 응답 보안 헤더 — nosniff, frame 막기(X-Frame-Options DENY + CSP frame-ancestors 'none'), CSP default-src 'none', Referrer-Policy no-referrer, no-store")
    void securityHeaders() throws Exception {
        Browser b = browser("198.51.100.50").withCsrf();
        for (String url : new String[] {"/api/v1/health", "/api/v1/me"}) {
            b.get(url)
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        }
    }

    // ------------------------------------------------------------------ AC2 요청 크기·요청 제한

    @Test
    @DisplayName("AC2: 요청 본문은 1MB까지 — 넘으면 본문을 읽지 않고 413 CONTENT_TOO_LARGE")
    void requestSizeLimit() throws Exception {
        Browser b = loggedIn(KIM, PW, "198.51.100.51");
        String big = "가".repeat(400_000); // UTF-8 3바이트 × 40만 = 1.2MB
        b.postJson("/api/v1/memos", "{\"title\":\"큰 메모\",\"content\":\"" + big + "\"}")
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("CONTENT_TOO_LARGE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memos WHERE user_id = ?", Integer.class, kim)).isZero();
        b.postJson("/api/v1/memos", "{\"title\":\"보통 메모\",\"content\":\"" + "가".repeat(10_000) + "\"}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC2: 로그인한 회원의 일반 API는 분당 300번까지 — 301번째 429(RATE_LIMITED api-user 감사 기록), 다른 회원은 영향 없음")
    void apiUserLimit() throws Exception {
        Browser b = loggedIn(KIM, PW, "198.51.100.52");
        for (int i = 0; i < 300; i++) {
            b.get("/api/v1/categories").andExpect(status().isOk());
        }
        b.get("/api/v1/categories").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        assertThat(jdbc.queryForList("SELECT CAST(detail AS CHAR) FROM audit_logs WHERE user_id = ? AND event = 'RATE_LIMITED'", String.class, kim))
            .containsExactly("{\"rule\": \"api-user\"}");

        long lee = createUser("lee@test.local");
        setPassword(lee, PW);
        loggedIn("lee@test.local", PW, "198.51.100.52").get("/api/v1/categories").andExpect(status().isOk());
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

    // ------------------------------------------------------------------ AC4 일기·메모 암호화

    @Test
    @DisplayName("AC4: 일기 내용·메모 제목·내용은 DB에 AES-256-GCM 암호문으로 저장된다 (원문이 안 보이고, 같은 내용도 매번 다름) — API로는 원문")
    void notesEncryptedAtRest() throws Exception {
        Browser b = loggedIn(KIM, PW, "198.51.100.60");
        b.perform(put("/api/v1/diaries/" + TODAY).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"비밀 일기 내용\"}"))
            .andExpect(status().is2xxSuccessful());
        b.postJson("/api/v1/memos", "{\"title\":\"비밀 제목\",\"content\":\"같은 메모\"}").andExpect(status().isCreated());
        b.postJson("/api/v1/memos", "{\"title\":\"비밀 제목\",\"content\":\"같은 메모\"}").andExpect(status().isCreated());

        byte[] diary = jdbc.queryForObject("SELECT content FROM diaries WHERE user_id = ?", byte[].class, kim);
        assertThat(diary[0]).isEqualTo((byte) 1);
        assertThat(new String(diary, StandardCharsets.UTF_8)).doesNotContain("비밀");
        List<byte[]> titles = jdbc.queryForList("SELECT title FROM memos WHERE user_id = ? ORDER BY memo_id", byte[].class, kim);
        List<byte[]> contents = jdbc.queryForList("SELECT content FROM memos WHERE user_id = ? ORDER BY memo_id", byte[].class, kim);
        assertThat(titles.get(0)).isNotEqualTo(titles.get(1));
        assertThat(contents.get(0)).isNotEqualTo(contents.get(1));
        assertThat(new String(contents.get(0), StandardCharsets.UTF_8)).doesNotContain("메모");

        b.get("/api/v1/diaries/" + TODAY).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("비밀 일기 내용"));
        b.get("/api/v1/memos").andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("비밀 제목"));
    }

    @Test
    @DisplayName("AC4: 암호화 전에 저장된 원문(기존 admin 데이터·로컬 예시 데이터)은 서버를 켤 때 한 번 읽어 암호화해 다시 저장한다 — 이미 암호문인 행은 건드리지 않음")
    void existingPlaintextIsEncrypted() throws Exception {
        jdbc.update("INSERT INTO diaries (user_id, diary_date, content) VALUES (?, '2026-09-01', ?)", kim, "예전 일기".getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO memos (user_id, title, content) VALUES (?, ?, NULL)", kim, "예전 메모".getBytes(StandardCharsets.UTF_8));
        Browser b = loggedIn(KIM, PW, "198.51.100.61");
        b.get("/api/v1/diaries/2026-09-01").andExpect(status().isOk()).andExpect(jsonPath("$.content").value("예전 일기"));

        assertThat(encryptor.encryptPlaintext()).isEqualTo(2);
        assertThat(encryptor.encryptPlaintext()).isZero();
        assertThat(jdbc.queryForObject("SELECT content FROM diaries WHERE user_id = ?", byte[].class, kim)[0]).isEqualTo((byte) 1);
        assertThat(jdbc.queryForObject("SELECT title FROM memos WHERE user_id = ?", byte[].class, kim)[0]).isEqualTo((byte) 1);
        assertThat(jdbc.queryForObject("SELECT content FROM memos WHERE user_id = ?", byte[].class, kim)).isNull();
        b.get("/api/v1/diaries/2026-09-01").andExpect(jsonPath("$.content").value("예전 일기"));
        b.get("/api/v1/memos").andExpect(jsonPath("$[0].title").value("예전 메모"));
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
    @DisplayName("지표는 Prometheus 형식으로 내보낸다 (이름 jp_auth_login_total …). Actuator는 앱 포트와 다른 관리 포트에서 health·prometheus만 연다")
    void prometheusOnManagementPortOnly() throws Exception {
        browser("198.51.100.44").withCsrf().login(KIM, PW).andExpect(status().isOk());
        assertThat(prometheus.scrape()).contains("jp_auth_login_total{method=\"PASSWORD\",result=\"SUCCESS\"}");
        // 테스트(MockMvc)는 관리 포트를 따로 띄우지 않고 테스트 설정이 메인 application.yml을 대신해서, 메인 설정 파일 값으로 확인한다.
        // 실제 서버 확인: 8080/actuator/health 404, 8081/actuator/prometheus 200
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties main = yaml.getObject();
        assertThat(main.getProperty("management.server.port")).isEqualTo("${JP_MANAGEMENT_PORT:8081}");
        assertThat(main.getProperty("server.port")).isEqualTo("8080");
        assertThat(main.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,prometheus");
        browser("198.51.100.45").get("/actuator/env").andExpect(status().isNotFound());
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
