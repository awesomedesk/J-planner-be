package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.api.v1.auth.SignupTokens;
import com.awesomedesk.j_planner.support.RecordingMailer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * US-35 사용자는 비밀번호를 잊으면 재설정한다 (AUTH-13).
 * 설계: 08-api-design.md 13-3(/auth/password/forgot·reset)·13-6·13-7·13-8, 07 11-2(auth_tokens).
 * <p>
 * AC: 항상 같은 안내. 링크 1회용·30분·해시 저장·새 요청 시 이전 무효·설정 도메인만. 재설정 후 자동 로그인 없음, 모든 기기 로그아웃, 알림 메일.
 * 메일은 실제로 보내지 않고 RecordingMailer가 기억한다.
 * <p>
 * FE 담당: '비밀번호 찾기' 화면(이메일 입력 → 늘 같은 안내 '메일을 보냈어요. 메일이 안 오면 이메일 주소를 확인해 주세요'),
 * 메일 링크 화면(/password/reset?token=…)에서 새 비밀번호 입력 → POST /auth/password/reset → '비밀번호를 바꿨어요. 로그인해 주세요'.
 */
@DisplayName("US-35 비밀번호 재설정")
class US35PasswordResetAcceptanceTest extends BrowserAcceptanceTest {

    private static final String FORGOT = "/api/v1/auth/password/forgot";
    private static final String RESET = "/api/v1/auth/password/reset";
    private static final String ME = "/api/v1/me";
    private static final String LINK_PREFIX = "http://localhost:3000/password/reset?token=";

    private static final String EMAIL = "member@test.local";
    private static final String OLD_PASSWORD = "old-password-12345";
    private static final String NEW_PASSWORD = "brand-new-password-678";

    private long member;
    private Browser b;

    @BeforeEach
    void setUp() throws Exception {
        member = createUser(EMAIL);
        setPassword(member, OLD_PASSWORD);
        b = browser("192.0.2.1").withCsrf();
    }

    // ------------------------------------------------------------------ AC1 항상 같은 안내

    @Test
    @DisplayName("AC1: 비밀번호 찾기는 항상 202 — 회원이면 재설정 메일, 없는 이메일·임시 가입 중인 이메일은 메일 없이 같은 202")
    void alwaysSameAnswer() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        assertThat(mailer.sentTo(EMAIL, "RESET")).hasSize(1);
        assertThat(mailer.sentTo(EMAIL).getFirst().link()).startsWith(LINK_PREFIX);

        forgot(b, "nobody@test.local").andExpect(status().isAccepted());
        b.postJson("/api/v1/auth/signup",
            "{\"email\":\"pending@test.local\",\"password\":\"pending-password-123\",\"name\":\"임시\",\"ageOver14\":true}")
            .andExpect(status().isAccepted());
        forgot(b, "pending@test.local").andExpect(status().isAccepted());

        assertThat(mailer.sentTo("nobody@test.local")).isEmpty();
        assertThat(mailer.sentTo("pending@test.local", "RESET")).isEmpty();
    }

    @Test
    @DisplayName("AC1: 이메일은 앞뒤 공백·대문자를 정리해서 찾고, 형식이 틀리면 400 (errors에 email)")
    void emailNormalized() throws Exception {
        forgot(b, "  Member@Test.LOCAL ").andExpect(status().isAccepted());
        assertThat(mailer.sentTo(EMAIL, "RESET")).hasSize(1);
        forgot(b, "not-an-email")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    @DisplayName("AC1: 비밀번호가 아직 없는 회원(admin 첫 로그인 전)도 메일을 받아 비밀번호를 만든다")
    void memberWithoutPassword() throws Exception {
        forgot(b, "admin@test.local").andExpect(status().isAccepted());
        reset(b, lastToken("admin@test.local"), NEW_PASSWORD).andExpect(status().isNoContent());
        b.login("admin@test.local", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC1: 같은 이메일은 15분에 3번까지 메일을 보낸다 (IP가 달라도) — 그 뒤도 202, 메일만 안 감")
    void limitPerEmail() throws Exception {
        for (int i = 0; i < 4; i++) {
            forgot(browser("192.0.2." + (10 + i)).withCsrf(), EMAIL).andExpect(status().isAccepted());
        }
        assertThat(mailer.sentTo(EMAIL, "RESET")).hasSize(3);
    }

    @Test
    @DisplayName("AC1: 한 IP에서는 1시간에 20번까지 — 그 뒤도 202, 메일만 안 감 (감사 기록 RATE_LIMITED forgot-ip)")
    void limitPerIp() throws Exception {
        Browser same = browser("192.0.2.20").withCsrf();
        for (int i = 0; i < 20; i++) {
            forgot(same, "nobody" + i + "@test.local").andExpect(status().isAccepted());
        }
        forgot(same, EMAIL).andExpect(status().isAccepted());
        assertThat(mailer.sentTo(EMAIL)).isEmpty();
        assertThat(jdbc.queryForList("SELECT CAST(detail AS CHAR) FROM audit_logs WHERE event = 'RATE_LIMITED'", String.class))
            .anyMatch(d -> d.contains("forgot-ip"));
    }

    // ------------------------------------------------------------------ AC1 링크

    @Test
    @DisplayName("AC1: 링크로 새 비밀번호를 정하면 204 — 새 비밀번호로 로그인되고 이전 비밀번호는 안 된다")
    void resetPassword() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        reset(b, lastToken(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());

        b.login(EMAIL, OLD_PASSWORD).andExpect(status().isUnauthorized());
        b.login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC1: 링크는 1회용이다 (두 번째는 400 TOKEN_INVALID), 없는·잘못된 토큰도 같은 400")
    void singleUse() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        reset(b, token, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(b, token, "another-password-999").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        reset(b, "not-a-real-token", NEW_PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        b.login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC1: 링크는 30분 유효하다 (29분은 되고 31분은 TOKEN_INVALID)")
    void validFor30Minutes() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        clock.advance(Duration.ofMinutes(31));
        reset(b, token, NEW_PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        clock.reset();
        redis.delete(redis.keys("jp:rl:*"));
        forgot(b, EMAIL).andExpect(status().isAccepted());
        String second = lastToken(EMAIL);
        clock.advance(Duration.ofMinutes(29));
        reset(b, second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("AC1: 새로 요청하면 이전 링크는 못 쓴다")
    void newRequestInvalidatesOld() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        String first = lastToken(EMAIL);
        forgot(b, EMAIL).andExpect(status().isAccepted());
        String second = lastToken(EMAIL);
        assertThat(second).isNotEqualTo(first);

        reset(b, first, NEW_PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        reset(b, second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("AC1: DB에는 토큰 원문이 아니라 SHA-256 해시만 저장한다 (auth_tokens, purpose PASSWORD_RESET)")
    void tokenStoredAsHash() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM auth_tokens WHERE user_id = ?", member);
        assertThat(row).containsEntry("purpose", "PASSWORD_RESET");
        assertThat((byte[]) row.get("token_hash")).hasSize(32).isEqualTo(SignupTokens.hash(token));
        assertThat(row.values().stream().map(String::valueOf)).noneMatch(v -> v.contains(token));
    }

    @Test
    @DisplayName("AC1: 링크 주소는 설정한 FE 주소만 쓴다 — 요청의 Host 헤더를 바꿔도 따라가지 않는다")
    void linkUsesConfiguredDomainOnly() throws Exception {
        b.perform(post(FORGOT).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + EMAIL + "\"}")
                .header("Host", "evil.example.com")
                .header("X-Forwarded-Host", "evil.example.com"))
            .andExpect(status().isAccepted());
        assertThat(mailer.sentTo(EMAIL).getFirst().link()).startsWith(LINK_PREFIX).doesNotContain("evil");
    }

    @Test
    @DisplayName("AC1: 새 비밀번호도 가입과 같은 규칙 — 15자 미만·유출이면 400 PASSWORD_REJECTED (errors에 newPassword), 링크는 그대로 쓸 수 있다")
    void newPasswordRules() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        for (String bad : List.of("short-password", "password1234567890")) {
            reset(b, token, bad)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REJECTED"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        }
        reset(b, token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("AC1: 링크 확인(reset)은 IP별 분당 10회까지 — 11번째는 429")
    void resetLimitPerIp() throws Exception {
        Browser same = browser("192.0.2.30").withCsrf();
        for (int i = 0; i < 10; i++) {
            reset(same, "wrong-token-" + i, NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        reset(same, "wrong-token-10", NEW_PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    // ------------------------------------------------------------------ AC2 재설정 뒤

    @Test
    @DisplayName("AC2: 재설정해도 자동 로그인하지 않는다 — 세션 쿠키 없음, /me 401")
    void noAutoLogin() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        Browser mailApp = browser("192.0.2.40").withCsrf();
        ResultActions done = reset(mailApp, lastToken(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());
        assertThat(done.andReturn().getResponse().getCookie(Browser.SESSION_COOKIE)).isNull();
        mailApp.get(ME).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC2: 재설정하면 그 회원의 모든 기기가 로그아웃된다 (다른 회원은 그대로)")
    void logsOutAllDevices() throws Exception {
        Browser pc = loggedIn(EMAIL, OLD_PASSWORD, "192.0.2.50");
        Browser phone = loggedIn(EMAIL, OLD_PASSWORD, "192.0.2.51");
        setPassword(ADMIN_ID, "admin-password-1234");
        Browser admin = loggedIn("admin@test.local", "admin-password-1234", "192.0.2.52");

        forgot(b, EMAIL).andExpect(status().isAccepted());
        reset(b, lastToken(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());

        pc.get(ME).andExpect(status().isUnauthorized());
        phone.get(ME).andExpect(status().isUnauthorized());
        admin.get(ME).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC2: 재설정하면 알림 메일을 보낸다 (링크 없음)")
    void notificationMail() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        reset(b, lastToken(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());
        List<RecordingMailer.Sent> notices = mailer.sentTo(EMAIL, "PASSWORD_CHANGED");
        assertThat(notices).hasSize(1);
        assertThat(notices.getFirst().link()).isNull();
    }

    @Test
    @DisplayName("AC2: 재설정하면 로그인 실패 대기가 풀린다 (5번 틀려 막힌 계정도 바로 로그인)")
    void clearsLoginBackoff() throws Exception {
        Browser other = browser("192.0.2.60").withCsrf();
        for (int i = 0; i < 5; i++) {
            other.login(EMAIL, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        other.login(EMAIL, OLD_PASSWORD).andExpect(status().isTooManyRequests());

        forgot(b, EMAIL).andExpect(status().isAccepted());
        reset(b, lastToken(EMAIL), NEW_PASSWORD).andExpect(status().isNoContent());
        other.login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC2: 감사 기록 PASSWORD_CHANGED {method: RESET} — 이메일·비밀번호·토큰은 남기지 않는다")
    void audit() throws Exception {
        forgot(b, EMAIL).andExpect(status().isAccepted());
        String token = lastToken(EMAIL);
        reset(b, token, NEW_PASSWORD).andExpect(status().isNoContent());

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, CAST(detail AS CHAR) AS detail FROM audit_logs WHERE event = 'PASSWORD_CHANGED'");
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.getFirst().get("user_id")).longValue()).isEqualTo(member);
        assertThat((String) rows.getFirst().get("detail")).contains("RESET");
        String all = jdbc.queryForList("SELECT CONCAT_WS('|', user_id, event, ip, user_agent, CAST(detail AS CHAR)) FROM audit_logs", String.class).toString();
        assertThat(all).doesNotContain(EMAIL, NEW_PASSWORD, OLD_PASSWORD, token);
    }

    // ------------------------------------------------------------------ 준비

    private static ResultActions forgot(Browser browser, String email) throws Exception {
        return browser.postJson(FORGOT, "{\"email\":\"" + email + "\"}");
    }

    private static ResultActions reset(Browser browser, String token, String newPassword) throws Exception {
        return browser.postJson(RESET, "{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}");
    }

    /** 그 주소로 마지막에 보낸 재설정 메일의 토큰 */
    private String lastToken(String email) {
        List<RecordingMailer.Sent> mails = mailer.sentTo(email, "RESET");
        assertThat(mails).as(email + "로 보낸 재설정 메일").isNotEmpty();
        String link = mails.getLast().link();
        assertThat(link).startsWith(LINK_PREFIX);
        return link.substring(LINK_PREFIX.length());
    }
}
