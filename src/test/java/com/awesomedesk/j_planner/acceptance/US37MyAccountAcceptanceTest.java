package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.api.v1.auth.SignupTokens;
import com.awesomedesk.j_planner.support.FakeGoogle;
import com.awesomedesk.j_planner.support.FakeGoogle.GoogleUser;
import com.awesomedesk.j_planner.support.FakeGoogle.Tamper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * US-37 사용자는 내 계정에서 정보·비밀번호·로그인 수단·로그인 기기를 관리하고 탈퇴한다 (AUTH-04·15).
 * 설계: 08-api-design.md 13-1·13-4·13-6·13-7·13-8, 07 11-2(auth_tokens EMAIL_CHANGE), D-060·D-061·D-066.
 * <p>
 * AC1: 이름·이메일 변경(새 주소 인증 링크 24시간, 인증 전까지 기존 이메일 유지 + 기존 주소 알림), 비밀번호 변경/추가,
 *      연결된 로그인 수단(연결·해제, 마지막 수단은 해제 불가 — US36GoogleLoginAcceptanceTest), 로그인 기기 목록·원격 로그아웃. 민감 작업은 재인증.
 * AC2: 탈퇴 — 재인증 → 확인 → 모든 로그인 끊고 데이터 즉시 삭제.
 * <p>
 * FE 담당: 내 계정 화면(/me) 배치·문구, 탈퇴 전 확인 창('모든 일정·Todo·일기·메모가 지워져요'), 새 이메일 인증 화면(/email/verify?token=, 버튼을 눌러야 보냄),
 * 비밀번호 없는 회원(구글만)의 재인증 버튼(→ /api/v1/me/reauth/google). 백업 삭제 주기는 처리방침(US-39·44).
 */
@DisplayName("US-37 내 계정·탈퇴")
class US37MyAccountAcceptanceTest extends BrowserAcceptanceTest {

    private static final String ME = "/api/v1/me";
    private static final String REAUTH = "/api/v1/me/reauth";
    private static final String REAUTH_GOOGLE = "/api/v1/me/reauth/google";
    private static final String EMAIL = "/api/v1/me/email";
    private static final String EMAIL_VERIFY = "/api/v1/me/email/verify";
    private static final String PASSWORD = "/api/v1/me/password";
    private static final String SESSIONS = "/api/v1/me/sessions";
    private static final String CALLBACK = "/api/v1/auth/google/callback";
    private static final String FE = "http://localhost:3000";
    private static final String EMAIL_LINK = FE + "/email/verify?token=";

    private static final String KIM = "kim@test.local";
    private static final String PW = "member-password-123";
    private static final String NEW_PW = "brand-new-password-678";

    @Autowired
    private FakeGoogle google;

    private long kim;

    @BeforeEach
    void setUp() {
        google.clear();
        kim = createUser(KIM);
        setPassword(kim, PW);
    }

    // ------------------------------------------------------------------ AC1 이름

    @Test
    @DisplayName("AC1: 이름 변경 PATCH /me — 앞뒤 공백 정리, 1~50자 (재인증 없이), 틀리면 400 errors name")
    void changeName() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.1");
        patchJson(b, ME, "{\"name\":\"  새 이름  \"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("새 이름")).andExpect(jsonPath("$.email").value(KIM));
        b.get(ME).andExpect(jsonPath("$.name").value("새 이름"));

        patchJson(b, ME, "{\"name\":\"   \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("name"));
        patchJson(b, ME, "{\"name\":\"" + "가".repeat(51) + "\"}").andExpect(status().isBadRequest());
        patchJson(b, ME, "{\"name\":\"" + "가".repeat(50) + "\"}").andExpect(status().isOk());
        patchJson(b, ME, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.name").value("가".repeat(50)));
    }

    // ------------------------------------------------------------------ AC1 이메일

    @Test
    @DisplayName("AC1: 이메일 변경은 재인증 5분 안에만 (403 REAUTH_REQUIRED)")
    void emailChangeNeedsReauth() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.2");
        putJson(b, EMAIL, "{\"email\":\"new@test.local\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        reauth(b);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        putJson(b, EMAIL, "{\"email\":\"new@test.local\"}").andExpect(status().isForbidden());
        assertThat(mailer.sent()).isEmpty();
    }

    @Test
    @DisplayName("AC1: 이메일 변경 요청 → 202, 새 주소로 인증 링크(24시간) + 기존 주소에 알림, 인증 전까지 기존 이메일 그대로(로그인도 기존 이메일)")
    void emailChangeRequest() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.3");
        reauth(b);
        putJson(b, EMAIL, "{\"email\":\"  New.Kim@Test.LOCAL \"}").andExpect(status().isAccepted());

        assertThat(mailer.sentTo("new.kim@test.local", "EMAIL_CHANGE")).hasSize(1);
        assertThat(mailer.sentTo("new.kim@test.local", "EMAIL_CHANGE").getFirst().link()).startsWith(EMAIL_LINK);
        assertThat(mailer.sentTo(KIM, "EMAIL_CHANGE_NOTICE")).hasSize(1);
        b.get(ME).andExpect(jsonPath("$.email").value(KIM));
        login(KIM, PW, "203.0.113.4");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_tokens WHERE user_id = ? AND purpose = 'EMAIL_CHANGE' AND new_email = 'new.kim@test.local'",
            Integer.class, kim)).isEqualTo(1);
    }

    @Test
    @DisplayName("AC1: 새 주소 인증 → 204(로그인 없이), 이메일 바뀜 + 모든 기기 로그아웃 + 감사 기록 EMAIL_CHANGED, 새 이메일로만 로그인")
    void emailChangeVerify() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.5");
        Browser other = login(KIM, PW, "203.0.113.6");
        reauth(b);
        putJson(b, EMAIL, "{\"email\":\"new.kim@test.local\"}").andExpect(status().isAccepted());

        verifyEmail(browser("203.0.113.7").withCsrf(), lastEmailToken("new.kim@test.local")).andExpect(status().isNoContent());

        b.get(ME).andExpect(status().isUnauthorized());
        other.get(ME).andExpect(status().isUnauthorized());
        browser("203.0.113.8").withCsrf().login(KIM, PW).andExpect(status().isUnauthorized());
        login("new.kim@test.local", PW, "203.0.113.9").get(ME).andExpect(jsonPath("$.email").value("new.kim@test.local"));
        assertThat(audits(kim)).contains("EMAIL_CHANGED");
    }

    @Test
    @DisplayName("AC1: 인증 링크는 1회용·24시간, 새 요청을 하면 이전 링크 무효, 없는 토큰도 같은 400 TOKEN_INVALID")
    void emailLinkRules() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.10");
        reauth(b);
        putJson(b, EMAIL, "{\"email\":\"first@test.local\"}").andExpect(status().isAccepted());
        String first = lastEmailToken("first@test.local");
        putJson(b, EMAIL, "{\"email\":\"second@test.local\"}").andExpect(status().isAccepted());
        String second = lastEmailToken("second@test.local");

        Browser anon = browser("203.0.113.11").withCsrf();
        verifyEmail(anon, first).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        verifyEmail(anon, "not-a-real-token").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        clock.advance(Duration.ofHours(24).plusMinutes(1));
        verifyEmail(anon, second).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        clock.reset();
        verifyEmail(anon, second).andExpect(status().isNoContent());
        verifyEmail(anon, second).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, kim)).isEqualTo("second@test.local");
    }

    @Test
    @DisplayName("AC1: 바꿀 이메일이 이미 회원이면 409(loginMethods), 지금 이메일과 같거나 형식이 틀리면 400 errors email. 인증 사이에 회원이 생기면 TOKEN_INVALID")
    void emailConflicts() throws Exception {
        long lee = createUser("lee@test.local");
        setPassword(lee, PW);
        Browser b = login(KIM, PW, "203.0.113.12");
        reauth(b);
        putJson(b, EMAIL, "{\"email\":\"LEE@test.local\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED")).andExpect(jsonPath("$.loginMethods").value(contains("EMAIL")));
        putJson(b, EMAIL, "{\"email\":\"Kim@Test.local\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("email"));
        putJson(b, EMAIL, "{\"email\":\"not-an-email\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("email"));

        putJson(b, EMAIL, "{\"email\":\"park@test.local\"}").andExpect(status().isAccepted());
        createUser("park@test.local");
        verifyEmail(browser("203.0.113.13").withCsrf(), lastEmailToken("park@test.local"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, kim)).isEqualTo(KIM);
    }

    @Test
    @DisplayName("AC1: 바꿀 이메일로 임시 가입 중이면 인증할 때 그 임시 가입을 지운다 (이메일 하나에 계정 하나)")
    void pendingSignupRemovedOnVerify() throws Exception {
        browser("203.0.113.14").withCsrf().postJson("/api/v1/auth/signup",
            "{\"email\":\"new.kim@test.local\",\"password\":\"pending-password-123\",\"name\":\"임시\",\"ageOver14\":true}").andExpect(status().isAccepted());
        Browser b = login(KIM, PW, "203.0.113.15");
        reauth(b);
        putJson(b, EMAIL, "{\"email\":\"new.kim@test.local\"}").andExpect(status().isAccepted());
        verifyEmail(browser("203.0.113.16").withCsrf(), lastEmailToken("new.kim@test.local")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_signups WHERE email = 'new.kim@test.local'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("AC1: 이메일 변경 요청은 회원마다 하루 5번까지 (6번째 429, RATE_LIMITED email-change-user), 인증 링크 확인은 IP마다 분당 10번 (email-verify-ip)")
    void emailRateLimits() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.17");
        reauth(b);
        for (int i = 0; i < 5; i++) {
            putJson(b, EMAIL, "{\"email\":\"n" + i + "@test.local\"}").andExpect(status().isAccepted());
        }
        putJson(b, EMAIL, "{\"email\":\"n5@test.local\"}").andExpect(status().isTooManyRequests());

        Browser anon = browser("203.0.113.18").withCsrf();
        for (int i = 0; i < 10; i++) {
            verifyEmail(anon, "wrong-token-" + i).andExpect(status().isBadRequest());
        }
        verifyEmail(anon, "wrong-token-x").andExpect(status().isTooManyRequests());
        assertThat(jdbc.queryForList("SELECT CAST(detail AS CHAR) FROM audit_logs WHERE event = 'RATE_LIMITED'", String.class))
            .anyMatch(d -> d.contains("email-change-user")).anyMatch(d -> d.contains("email-verify-ip"));
    }

    // ------------------------------------------------------------------ AC1 비밀번호

    @Test
    @DisplayName("AC1: 비밀번호 변경 — 재인증 뒤 204, 이 기기는 그대로·다른 기기 로그아웃, 알림 메일, PASSWORD_CHANGED {method: CHANGE}, 새 비밀번호로만 로그인")
    void changePassword() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.20");
        Browser other = login(KIM, PW, "203.0.113.21");
        putJson(b, PASSWORD, "{\"newPassword\":\"" + NEW_PW + "\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        reauth(b);
        putJson(b, PASSWORD, "{\"newPassword\":\"" + NEW_PW + "\"}").andExpect(status().isNoContent());

        b.get(ME).andExpect(status().isOk());
        other.get(ME).andExpect(status().isUnauthorized());
        browser("203.0.113.22").withCsrf().login(KIM, PW).andExpect(status().isUnauthorized());
        login(KIM, NEW_PW, "203.0.113.23");
        assertThat(mailer.sentTo(KIM, "PASSWORD_CHANGED")).hasSize(1);
        assertThat(audits(kim)).contains("PASSWORD_CHANGED {\"method\": \"CHANGE\"}");
    }

    @Test
    @DisplayName("AC1: 새 비밀번호는 가입과 같은 규칙 — 15자 미만·128자 초과·유출이면 400 PASSWORD_REJECTED (errors newPassword), 바뀌지 않음")
    void passwordRules() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.24");
        reauth(b);
        for (String bad : List.of("short-pass", "a".repeat(129), "password1234567890")) {
            putJson(b, PASSWORD, "{\"newPassword\":\"" + bad + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_REJECTED")).andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        }
        login(KIM, PW, "203.0.113.25");
    }

    @Test
    @DisplayName("AC1: 구글만 있는 회원은 구글로 재인증(prompt=login)한 뒤 비밀번호를 추가한다 → hasPassword true, loginMethods EMAIL·GOOGLE")
    void googleMemberAddsPassword() throws Exception {
        GoogleUser hong = new GoogleUser("google-sub-hong", "hong@gmail.com", true, "홍길동");
        Browser g = googleSignup(hong, "203.0.113.26");
        g.withCsrf();

        g.postJson(REAUTH, "{\"password\":\"anything-at-all-123\"}").andExpect(status().isUnauthorized());
        String authorize = start(g, REAUTH_GOOGLE);
        Map<String, String> q = FakeGoogle.query(authorize);
        assertThat(q.get("prompt")).isEqualTo("login");
        assertThat(q.get("max_age")).isEqualTo("0");
        assertThat(q.get("code_challenge_method")).isEqualTo("S256");
        assertThat(callback(g, authorize, hong, Tamper.NONE)).isEqualTo(FE + "/me?reauth=google");

        putJson(g, PASSWORD, "{\"newPassword\":\"" + NEW_PW + "\"}").andExpect(status().isNoContent());
        g.get(ME).andExpect(jsonPath("$.hasPassword").value(true)).andExpect(jsonPath("$.loginMethods").value(contains("EMAIL", "GOOGLE")));
        login("hong@gmail.com", NEW_PW, "203.0.113.27");
    }

    @Test
    @DisplayName("AC1: 구글 재인증은 내 구글로 방금 다시 로그인했을 때만 — 다른 구글이면 /me?error=GOOGLE_ACCOUNT_MISMATCH, 다시 로그인 안 했으면(auth_time 5분 지남) GOOGLE_FAILED, 구글 없으면 404, 로그인 안 했으면 401")
    void googleReauthRules() throws Exception {
        GoogleUser hong = new GoogleUser("google-sub-hong", "hong@gmail.com", true, "홍길동");
        Browser g = googleSignup(hong, "203.0.113.28");
        g.withCsrf();

        GoogleUser other = new GoogleUser("google-sub-other", "other@gmail.com", true, "남");
        assertThat(callback(g, start(g, REAUTH_GOOGLE), other, Tamper.NONE)).isEqualTo(FE + "/me?error=GOOGLE_ACCOUNT_MISMATCH");
        assertThat(callback(g, start(g, REAUTH_GOOGLE), hong, Tamper.STALE_AUTH_TIME)).isEqualTo(FE + "/me?error=GOOGLE_FAILED");
        g.get(ME).andExpect(status().isOk());
        putJson(g, PASSWORD, "{\"newPassword\":\"" + NEW_PW + "\"}").andExpect(status().isForbidden());
        assertThat(userCount()).isEqualTo(3);

        login(KIM, PW, "203.0.113.29").get(REAUTH_GOOGLE).andExpect(status().isNotFound());
        browser("203.0.113.30").get(REAUTH_GOOGLE).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ AC1 로그인 기기

    @Test
    @DisplayName("AC1: 로그인 기기 목록 — 기기마다 id·기기 이름·로그인 시각·마지막 사용·IP 앞부분·지금 기기, id는 세션 ID가 아니다")
    void listSessions() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.31");
        login(KIM, PW, "198.51.100.32");

        ResultActions list = b.get(SESSIONS).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.current == true)].ipPrefix").value(contains("203.0.*.*")))
            .andExpect(jsonPath("$[?(@.current == false)].ipPrefix").value(contains("198.51.*.*")))
            .andExpect(jsonPath("$[0].device").value("Chrome · macOS"));
        List<String> ids = read(list, "$[*].id");
        assertThat(ids).doesNotContain(sessionId(b));
        assertThat(read(list, "$[0].loggedInAt").toString()).isEqualTo("2026-09-25T09:00:00");
    }

    @Test
    @DisplayName("AC1: 원격 로그아웃 DELETE /me/sessions/{id} — 그 기기만 로그아웃(204), 없는·남의 기기 id는 404")
    void remoteLogout() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.33");
        Browser phone = login(KIM, PW, "198.51.100.34");
        long lee = createUser("lee@test.local");
        setPassword(lee, PW);
        Browser leeBrowser = login("lee@test.local", PW, "203.0.113.35");

        ResultActions list = b.get(SESSIONS);
        List<String> others = read(list, "$[?(@.current == false)].id");
        b.perform(delete(SESSIONS + "/" + others.getFirst())).andExpect(status().isNoContent());
        phone.get(ME).andExpect(status().isUnauthorized());
        b.get(ME).andExpect(status().isOk());
        b.get(SESSIONS).andExpect(jsonPath("$.length()").value(1));

        String leeId = read(leeBrowser.get(SESSIONS), "$[0].id");
        b.perform(delete(SESSIONS + "/" + leeId)).andExpect(status().isNotFound());
        b.perform(delete(SESSIONS + "/no-such-device")).andExpect(status().isNotFound());
        leeBrowser.get(ME).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ AC2 탈퇴

    @Test
    @DisplayName("AC2: 탈퇴는 재인증 뒤에만 (403), 탈퇴하면 204 + 모든 기기 로그아웃 + 회원·작성 데이터 즉시 삭제 + 감사 기록 ACCOUNT_DELETED + 알림 메일")
    void deleteAccount() throws Exception {
        Browser b = login(KIM, PW, "203.0.113.40");
        Browser other = login(KIM, PW, "203.0.113.41");
        b.postJson("/api/v1/categories", "{\"name\":\"내 것\"}").andExpect(status().isCreated());
        b.postJson("/api/v1/todos", "{\"title\":\"할 일\",\"type\":\"DAY\",\"startDate\":\"2026-09-25\",\"endDate\":\"2026-09-25\"}")
            .andExpect(status().isCreated());
        b.postJson("/api/v1/memos", "{\"title\":\"메모\",\"content\":\"내용\"}").andExpect(status().isCreated());
        long adminCategories = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE user_id = 1", Long.class);

        b.perform(delete(ME)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        reauth(b);
        putJson(b, EMAIL, "{\"email\":\"pending.change@test.local\"}").andExpect(status().isAccepted());
        b.perform(delete(ME)).andExpect(status().isNoContent());

        b.get(ME).andExpect(status().isUnauthorized());
        other.get(ME).andExpect(status().isUnauthorized());
        browser("203.0.113.42").withCsrf().login(KIM, PW).andExpect(status().isUnauthorized());
        for (String table : List.of("users", "categories", "todos", "memos", "diaries", "ddays", "calendars", "user_settings", "sidebar_items",
            "password_credentials", "auth_tokens", "user_identities")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, kim)).as(table).isZero();
        }
        assertThat(audits(kim)).contains("ACCOUNT_DELETED");
        assertThat(mailer.sentTo(KIM, "ACCOUNT_DELETED")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE user_id = 1", Long.class)).isEqualTo(adminCategories);

        browser("203.0.113.43").withCsrf().postJson("/api/v1/auth/signup",
            "{\"email\":\"" + KIM + "\",\"password\":\"again-password-123\",\"name\":\"다시\",\"ageOver14\":true}").andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("AC2: 구글만 있는 회원도 구글 재인증 뒤 탈퇴한다 — 구글 연결도 지워져 같은 구글로 다시 가입할 수 있다")
    void googleMemberDeletes() throws Exception {
        GoogleUser hong = new GoogleUser("google-sub-hong", "hong@gmail.com", true, "홍길동");
        Browser g = googleSignup(hong, "203.0.113.44");
        g.withCsrf();
        long hongId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = 'hong@gmail.com'", Long.class);
        callback(g, start(g, REAUTH_GOOGLE), hong, Tamper.NONE);
        g.perform(delete(ME)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_identities WHERE user_id = ?", Integer.class, hongId)).isZero();

        Browser again = browser("203.0.113.45");
        assertThat(callback(again, start(again, "/api/v1/auth/google"), hong, Tamper.NONE)).isEqualTo(FE + "/signup/google"); // 처음 보는 구글 (D-083)
    }

    // ------------------------------------------------------------------ 도우미

    private Browser login(String email, String password, String ip) throws Exception {
        return loggedIn(email, password, ip);
    }

    private void reauth(Browser b) throws Exception {
        b.postJson(REAUTH, "{\"password\":\"" + PW + "\"}").andExpect(status().isNoContent());
    }

    private static ResultActions putJson(Browser b, String url, String json) throws Exception {
        return b.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static ResultActions patchJson(Browser b, String url, String json) throws Exception {
        return b.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static ResultActions verifyEmail(Browser b, String token) throws Exception {
        return b.postJson(EMAIL_VERIFY, "{\"token\":\"" + token + "\"}");
    }

    /** 그 주소로 마지막에 보낸 이메일 변경 링크의 토큰 (DB에는 해시만 있는지도 확인) */
    private String lastEmailToken(String to) {
        String link = mailer.sentTo(to, "EMAIL_CHANGE").getLast().link();
        String token = link.substring(EMAIL_LINK.length());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_tokens WHERE token_hash = ?", Integer.class, (Object) SignupTokens.hash(token)))
            .isEqualTo(1);
        return token;
    }

    /** 구글로 가입 (D-083: 콜백 → 확인 화면 /signup/google → 가입하기) — 로그인된 브라우저 */
    private Browser googleSignup(GoogleUser user, String ip) throws Exception {
        Browser g = browser(ip);
        assertThat(callback(g, start(g, "/api/v1/auth/google"), user, Tamper.NONE)).isEqualTo(FE + "/signup/google");
        g.withCsrf();
        g.postJson("/api/v1/auth/google/signup", "{\"name\":\"" + user.name() + "\",\"ageOver14\":true}").andExpect(status().isCreated());
        return g;
    }

    private static String start(Browser b, String url) throws Exception {
        return b.getEncoded(url).andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
    }

    private String callback(Browser b, String authorize, GoogleUser user, Tamper tamper) throws Exception {
        String code = google.approve(authorize, user, tamper);
        String state = FakeGoogle.query(authorize).get("state");
        return b.getEncoded(CALLBACK + "?code=" + enc(code) + "&state=" + enc(state))
            .andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
    }

    private static String sessionId(Browser b) {
        return new String(Base64.getDecoder().decode(b.sid.getValue()), StandardCharsets.UTF_8);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private int userCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
    }

    private List<String> audits(long userId) {
        return jdbc.queryForList("SELECT CONCAT(event, IFNULL(CONCAT(' ', CAST(detail AS CHAR)), '')) FROM audit_logs WHERE user_id = ? ORDER BY audit_id",
            String.class, userId);
    }
}
