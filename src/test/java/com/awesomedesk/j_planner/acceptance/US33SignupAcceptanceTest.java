package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.api.v1.auth.PendingSignupCleaner;
import com.awesomedesk.j_planner.api.v1.auth.SignupTokens;
import com.awesomedesk.j_planner.support.RecordingMailer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * US-33 사용자는 이메일로 가입하고 이메일을 인증한다 (AUTH-01·12, D-061·D-066·D-074).
 * 설계: 08-api-design.md 13-3(/auth/signup·resend·verify)·13-6(오류 코드)·13-7(요청 횟수 제한)·13-8(감사 기록), 07 11-2(pending_signups).
 * <p>
 * 메일은 실제로 보내지 않고 {@link RecordingMailer}가 기억한다. 메일 안의 링크에서 토큰을 꺼내 인증한다.
 * 유출 비밀번호는 {@code FakeBreachedPasswords} 목록으로 본다 (실제는 HIBP k-익명성 API).
 * 비밀번호 길이 규칙(글자 수 = 코드 포인트)은 PasswordPolicyTest, 토큰 모양·해시는 SignupTokensTest.
 * <p>
 * FE 담당: 가입 화면(이메일·비밀번호·이름·만 14세 확인 체크, 약관·개인정보처리방침 링크), 409일 때 loginMethods에 맞춘 버튼
 * ([로그인]/[비밀번호 찾기] 또는 [구글로 로그인]), 메일 링크 화면(/signup/verify?token=…)에서 POST /auth/signup/verify 후
 * '인증이 끝났어요. 로그인해 주세요'.
 */
@DisplayName("US-33 이메일 가입·이메일 인증")
class US33SignupAcceptanceTest extends BrowserAcceptanceTest {

    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String RESEND = "/api/v1/auth/signup/resend";
    private static final String VERIFY = "/api/v1/auth/signup/verify";
    private static final String ME = "/api/v1/me";

    private static final String EMAIL = "new@test.local";
    private static final String PASSWORD = "my-new-password-123";
    private static final String NAME = "새회원";
    private static final String LINK_PREFIX = "http://localhost:3000/signup/verify?token=";

    @Autowired
    private PendingSignupCleaner cleaner;

    private Browser b;

    @BeforeEach
    void setUp() throws Exception {
        b = browser("203.0.113.1").withCsrf();
    }

    // ------------------------------------------------------------------ AC1 입력값

    @Test
    @DisplayName("AC1: 이메일·비밀번호(15자 이상)·이름·만 14세 이상 확인을 보내면 202 — 임시 가입만 저장하고 인증 메일을 보낸다")
    void signupAccepted() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());

        assertThat(count("SELECT COUNT(*) FROM pending_signups WHERE email = ?", EMAIL)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", EMAIL)).isZero(); // 회원은 아직 없음
        List<RecordingMailer.Sent> mails = mailer.sentTo(EMAIL);
        assertThat(mails).hasSize(1);
        assertThat(mails.getFirst().type()).isEqualTo("SIGNUP");
        assertThat(mails.getFirst().name()).isEqualTo(NAME);
        assertThat(mails.getFirst().link()).startsWith(LINK_PREFIX);
    }

    @Test
    @DisplayName("AC1: 비밀번호는 원문을 저장하지 않는다 (Argon2id 해시)")
    void passwordIsHashed() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        String hash = jdbc.queryForObject("SELECT password_hash FROM pending_signups WHERE email = ?", String.class, EMAIL);
        assertThat(hash).startsWith("$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("AC1: 비밀번호가 15자 미만·128자 초과·유출 비밀번호면 400 PASSWORD_REJECTED (errors에 password). 조합 규칙은 없다")
    void passwordRules() throws Exception {
        for (String bad : List.of("short-pass-14c", "a".repeat(129), "password1234567890")) {
            signup(b, EMAIL, bad, NAME)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REJECTED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        }
        assertThat(mailer.sent()).isEmpty();
        // 조합 규칙 없음: 소문자만 15자도 된다
        signup(b, EMAIL, "aaaaaaaaaaaaaaa", NAME).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("AC1: 만 14세 이상 확인이 true가 아니면 400 VALIDATION_FAILED (errors에 ageOver14)")
    void ageOver14Required() throws Exception {
        b.postJson(SIGNUP, json(EMAIL, PASSWORD, NAME, "false"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("ageOver14"));
        b.postJson(SIGNUP, "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"" + NAME + "\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("ageOver14"));
        assertThat(count("SELECT COUNT(*) FROM pending_signups", null)).isZero();
    }

    @Test
    @DisplayName("AC5: 이름은 1~50자 (앞뒤 공백 제외), 비었거나 51자면 400 VALIDATION_FAILED (errors에 name)")
    void nameLength() throws Exception {
        for (String bad : List.of("", "   ", "가".repeat(51))) {
            signup(b, EMAIL, PASSWORD, bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        }
        signup(b, EMAIL, PASSWORD, "  " + "가".repeat(50) + "  ").andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT name FROM pending_signups WHERE email = ?", String.class, EMAIL))
            .isEqualTo("가".repeat(50));
    }

    @Test
    @DisplayName("AC1: 이메일 형식이 틀리면 400 (errors에 email), 앞뒤 공백·대문자는 정리해서 저장한다")
    void emailFormat() throws Exception {
        for (String bad : List.of("", "not-an-email", "a@b@c")) {
            signup(b, bad, PASSWORD, NAME)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
        }
        signup(b, "  New@Test.LOCAL ", PASSWORD, NAME).andExpect(status().isAccepted());
        assertThat(count("SELECT COUNT(*) FROM pending_signups WHERE email = ?", EMAIL)).isEqualTo(1);
        assertThat(mailer.sentTo(EMAIL)).hasSize(1);
    }

    // ------------------------------------------------------------------ AC2 이미 가입된 이메일·요청 제한

    @Test
    @DisplayName("AC2: 이미 가입된 이메일이면 409 EMAIL_ALREADY_REGISTERED '이미 가입된 이메일이에요' + loginMethods (이메일 계정 = EMAIL), 메일·임시 가입 없음")
    void alreadyRegistered() throws Exception {
        setPassword(ADMIN_ID, "admin-password-1234");
        signup(b, "Admin@Test.Local", PASSWORD, NAME)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(jsonPath("$.detail").value("이미 가입된 이메일이에요"))
            .andExpect(jsonPath("$.loginMethods.length()").value(1))
            .andExpect(jsonPath("$.loginMethods[0]").value("EMAIL"));
        assertThat(mailer.sent()).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM pending_signups", null)).isZero();
    }

    @Test
    @DisplayName("AC2: 비밀번호가 아직 없는 이메일 계정(admin 첫 로그인 전)도 409 + loginMethods [EMAIL] — [로그인]/[비밀번호 찾기]로 안내")
    void alreadyRegisteredWithoutPassword() throws Exception {
        signup(b, "admin@test.local", PASSWORD, NAME)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.loginMethods[0]").value("EMAIL"));
    }

    @Test
    @DisplayName("AC2: 입력값 검사가 409보다 먼저다 (가입된 이메일 + 짧은 비밀번호 → 400)")
    void validationBeforeConflict() throws Exception {
        signup(b, "admin@test.local", "short", NAME)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PASSWORD_REJECTED"));
    }

    @Test
    @DisplayName("AC2: 가입 요청은 IP별 10분에 5회까지 — 6번째는 429 (다른 IP는 영향 없음)")
    void signupLimitPerIp() throws Exception {
        Browser same = browser("203.0.113.2").withCsrf();
        for (int i = 0; i < 5; i++) {
            signup(same, "ip" + i + "@test.local", PASSWORD, NAME).andExpect(status().isAccepted());
        }
        signup(same, "ip5@test.local", PASSWORD, NAME)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        signup(browser("203.0.113.3").withCsrf(), "ip5@test.local", PASSWORD, NAME).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("AC2: 가입 요청은 IP별 하루 30회까지 (10분 창이 지나도)")
    void signupDailyLimitPerIp() throws Exception {
        Browser same = browser("203.0.113.4").withCsrf();
        for (int i = 0; i < 30; i++) {
            signup(same, "day" + i + "@test.local", PASSWORD, NAME).andExpect(status().isAccepted());
            redis.delete(redis.keys("jp:rl:signup-ip-10m:*")); // 10분 창이 지난 것처럼
        }
        signup(same, "day30@test.local", PASSWORD, NAME).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("AC2: 같은 이메일 가입 요청은 하루 5회까지 (메일 폭탄 방지, IP가 달라도) — 6번째는 429")
    void signupLimitPerEmail() throws Exception {
        for (int i = 0; i < 5; i++) {
            signup(browser("203.0.113." + (10 + i)).withCsrf(), EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        }
        signup(browser("203.0.113.20").withCsrf(), EMAIL, PASSWORD, NAME)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        assertThat(mailer.sentTo(EMAIL)).hasSize(5);
    }

    @Test
    @DisplayName("AC2: 요청 제한에 걸리면 감사 기록(RATE_LIMITED, rule = signup-ip / signup-email)이 남는다")
    void rateLimitAudit() throws Exception {
        Browser same = browser("203.0.113.21").withCsrf();
        for (int i = 0; i < 6; i++) {
            signup(same, "audit" + i + "@test.local", PASSWORD, NAME);
        }
        List<String> details = jdbc.queryForList(
            "SELECT CAST(detail AS CHAR) FROM audit_logs WHERE event = 'RATE_LIMITED'", String.class);
        assertThat(details).hasSize(1);
        assertThat(details.getFirst()).contains("signup-ip");
    }

    // ------------------------------------------------------------------ AC3 인증해야 회원·로그인

    @Test
    @DisplayName("AC3: 인증 링크를 눌러야 회원이 생기고 로그인할 수 있다 (기본 데이터 포함, 임시 가입은 지운다)")
    void verifyCreatesMember() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        b.login(EMAIL, PASSWORD).andExpect(status().isUnauthorized()); // 인증 전

        verify(b, lastToken(EMAIL)).andExpect(status().isCreated());

        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE user_id = ?", String.class, userId)).isEqualTo(NAME);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE user_id = ?", String.class, userId)).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM categories WHERE user_id = ? AND is_default = 'Y' AND name = '미지정'", userId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM user_settings WHERE user_id = ?", userId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sidebar_items WHERE user_id = ?", userId)).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM pending_signups", null)).isZero();

        b.login(EMAIL, PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(EMAIL))
            .andExpect(jsonPath("$.name").value(NAME))
            .andExpect(jsonPath("$.hasPassword").value(true))
            .andExpect(jsonPath("$.loginMethods[0]").value("EMAIL"));
    }

    @Test
    @DisplayName("AC3: 임시 가입 중인 이메일로 다시 가입하면 새 것으로 바꾼다 (이전 링크는 못 씀, 새 비밀번호·이름)")
    void signupAgainReplaces() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        String first = lastToken(EMAIL);
        signup(b, EMAIL, "second-password-456", "바뀐이름").andExpect(status().isAccepted());
        String second = lastToken(EMAIL);

        assertThat(count("SELECT COUNT(*) FROM pending_signups WHERE email = ?", EMAIL)).isEqualTo(1);
        verify(b, first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        verify(b, second).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE email = ?", String.class, EMAIL)).isEqualTo("바뀐이름");
        b.login(EMAIL, "second-password-456").andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC3: 임시 가입은 24시간 뒤 삭제된다 (그 뒤 링크는 TOKEN_INVALID, 매시간 정리 작업이 지운다)")
    void pendingExpiresAfter24Hours() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        signup(b, "keep@test.local", PASSWORD, NAME).andExpect(status().isAccepted());

        clock.advance(Duration.ofHours(23));
        assertThat(cleaner.deleteExpired()).isZero();
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        verify(b, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        assertThat(cleaner.deleteExpired()).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM pending_signups", null)).isZero();
    }

    @Test
    @DisplayName("AC3: 가입 인증을 기다리는 동안 같은 이메일이 다른 길로 회원이 되었으면 인증은 TOKEN_INVALID, 회원은 하나뿐이다")
    void memberCreatedMeanwhile() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        createUser(EMAIL); // 예: 구글 가입 (US-36)
        verify(b, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", EMAIL)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ AC4 인증 링크·다시 보내기

    @Test
    @DisplayName("AC4: 인증 링크는 1회용이다 (두 번째는 400 TOKEN_INVALID), 없는·잘못된 토큰도 같은 400")
    void tokenIsSingleUse() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        verify(b, token).andExpect(status().isCreated());
        verify(b, token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        verify(b, "not-a-real-token").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        b.postJson(VERIFY, "{}").andExpect(status().isBadRequest());
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", EMAIL)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC4: 인증 링크는 24시간 유효하다 (24시간 직전까지 됨)")
    void tokenValid24Hours() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        clock.advance(Duration.ofHours(24).minusMinutes(1));
        verify(b, lastToken(EMAIL)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC4: DB에는 토큰 원문이 아니라 SHA-256 해시만 저장한다")
    void tokenStoredAsHash() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        byte[] stored = jdbc.queryForObject("SELECT token_hash FROM pending_signups WHERE email = ?", byte[].class, EMAIL);
        assertThat(stored).hasSize(32).isEqualTo(SignupTokens.hash(token));
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM pending_signups WHERE email = ?", EMAIL);
        assertThat(row.values().stream().map(String::valueOf)).noneMatch(v -> v.contains(token));
    }

    @Test
    @DisplayName("AC4: 다시 보내기는 항상 202 — 60초 안이면 메일을 안 보내고, 60초가 지나면 새 링크를 보낸다 (이전 링크는 못 씀)")
    void resendEvery60Seconds() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        String first = lastToken(EMAIL);

        clock.advance(Duration.ofSeconds(59));
        resend(b, EMAIL).andExpect(status().isAccepted());
        assertThat(mailer.sentTo(EMAIL)).hasSize(1);

        clock.advance(Duration.ofSeconds(2));
        resend(b, " NEW@test.local ").andExpect(status().isAccepted());
        assertThat(mailer.sentTo(EMAIL)).hasSize(2);
        String second = lastToken(EMAIL);
        assertThat(second).isNotEqualTo(first);

        verify(b, first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        verify(b, second).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AC4: 다시 보내기는 임시 가입이 없거나(없는 이메일·이미 회원) 만료됐으면 메일 없이 202 — 가입 여부가 드러나지 않는다")
    void resendWithoutPending() throws Exception {
        resend(b, "nobody@test.local").andExpect(status().isAccepted());
        resend(b, "admin@test.local").andExpect(status().isAccepted());
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        clock.advance(Duration.ofHours(24).plusSeconds(1));
        resend(b, EMAIL).andExpect(status().isAccepted());
        assertThat(mailer.sent()).hasSize(1); // 가입 때 한 통뿐
        resend(b, "not-an-email").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("AC4: 다시 보내기는 이메일별 하루 10번까지 메일을 보낸다 (그 뒤도 202, 메일만 안 감)")
    void resendDailyLimit() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        for (int i = 0; i < 11; i++) {
            clock.advance(Duration.ofSeconds(61));
            resend(b, EMAIL).andExpect(status().isAccepted());
        }
        assertThat(mailer.sentTo(EMAIL)).hasSize(1 + 10);
    }

    @Test
    @DisplayName("AC4: 인증 링크 확인은 IP별 분당 10회까지 — 11번째는 429")
    void verifyLimitPerIp() throws Exception {
        Browser same = browser("203.0.113.30").withCsrf();
        for (int i = 0; i < 10; i++) {
            verify(same, "wrong-token-" + i).andExpect(status().isBadRequest());
        }
        verify(same, "wrong-token-10")
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    // ------------------------------------------------------------------ AC5 자동 로그인 안 함·감사 기록

    @Test
    @DisplayName("AC5: 인증 링크를 눌러도 자동 로그인하지 않는다 (D-066) — 세션 쿠키 없음, /me 401")
    void verifyDoesNotLogIn() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        Browser mailApp = browser("203.0.113.40").withCsrf(); // 메일 앱에서 링크를 연 다른 브라우저
        ResultActions verified = verify(mailApp, lastToken(EMAIL)).andExpect(status().isCreated());

        assertThat(verified.andReturn().getResponse().getCookie(Browser.SESSION_COOKIE)).isNull();
        assertThat(mailApp.sid).isNull();
        mailApp.get(ME).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC5: 가입이 끝나면 감사 기록 SIGNUP_COMPLETED {method: EMAIL} — 이메일·비밀번호·토큰은 남기지 않는다")
    void signupAudit() throws Exception {
        signup(b, EMAIL, PASSWORD, NAME).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        verify(b, token).andExpect(status().isCreated());
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, EMAIL);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, event, CAST(detail AS CHAR) AS detail FROM audit_logs WHERE event = 'SIGNUP_COMPLETED'");
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.getFirst().get("user_id")).longValue()).isEqualTo(userId);
        assertThat((String) rows.getFirst().get("detail")).contains("\"method\"", "EMAIL");
        String all = jdbc.queryForList("SELECT CONCAT_WS('|', user_id, event, ip, user_agent, CAST(detail AS CHAR)) FROM audit_logs", String.class).toString();
        assertThat(all).doesNotContain(EMAIL, PASSWORD, token);
    }

    // ------------------------------------------------------------------ 준비

    private static ResultActions signup(Browser browser, String email, String password, String name) throws Exception {
        return browser.postJson(SIGNUP, json(email, password, name, "true"));
    }

    private static ResultActions resend(Browser browser, String email) throws Exception {
        return browser.postJson(RESEND, "{\"email\":\"" + email + "\"}");
    }

    private static ResultActions verify(Browser browser, String token) throws Exception {
        return browser.postJson(VERIFY, "{\"token\":\"" + token + "\"}");
    }

    private static String json(String email, String password, String name, String ageOver14) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"name\":\"" + name + "\",\"ageOver14\":" + ageOver14 + "}";
    }

    /** 그 주소로 마지막에 보낸 인증 메일의 토큰 */
    private String lastToken(String email) {
        List<RecordingMailer.Sent> mails = mailer.sentTo(email);
        assertThat(mails).as(email + "로 보낸 메일").isNotEmpty();
        String link = mails.getLast().link();
        assertThat(link).startsWith(LINK_PREFIX);
        return link.substring(LINK_PREFIX.length());
    }

    private long count(String sql, Object arg) {
        Long n = arg == null ? jdbc.queryForObject(sql, Long.class) : jdbc.queryForObject(sql, Long.class, arg);
        return n == null ? 0 : n;
    }
}
