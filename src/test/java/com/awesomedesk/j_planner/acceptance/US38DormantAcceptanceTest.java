package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.api.v1.user.DormantAccounts;
import com.awesomedesk.j_planner.support.FakeGoogle;
import com.awesomedesk.j_planner.support.FakeGoogle.GoogleUser;
import com.awesomedesk.j_planner.support.FakeGoogle.Tamper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * US-38 1년 미로그인 계정은 안내 후 휴면이 되고, 다시 로그인하면 복원된다 (AUTH-09, D-061·D-069).
 * 설계: 07 11-2(users 휴면 칸, dormant_profiles), 08 13-3·13-6·13-8. 문서에 없던 부분은 BE 추천안(m20261031-be-po-us38, PO 확인 대기).
 * <p>
 * AC: 1년 미로그인 → 30일 전 메일 → 휴면(이메일·이름은 dormant_profiles로, 접근 제한) → 다시 로그인하면(비밀번호·연결된 구글 = 재인증) 복원.
 * 휴면 4년 뒤 삭제. 정지된 회원도 휴면은 진행, 복원해도 정지 유지 (D-069) — 정지 중에는 복원하지 않는다.
 * 휴면·삭제는 매일 새벽 작업({@link DormantAccounts}) — 테스트는 직접 부른다.
 * <p>
 * FE 담당: 로그인 응답 {@code restored: true}일 때 '휴면에서 돌아왔어요' 안내 (06 문구는 PO).
 */
@DisplayName("US-38 휴면")
class US38DormantAcceptanceTest extends BrowserAcceptanceTest {

    private static final String ME = "/api/v1/me";
    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String FORGOT = "/api/v1/auth/password/forgot";
    private static final String RESET = "/api/v1/auth/password/reset";
    private static final String REAUTH = "/api/v1/me/reauth";
    private static final String START = "/api/v1/auth/google";
    private static final String CALLBACK = "/api/v1/auth/google/callback";
    private static final String SIGNUP_GOOGLE = "/api/v1/auth/google/signup";
    private static final String FE = "http://localhost:3000";

    private static final String KIM = "kim@test.local";
    private static final String PW = "member-password-123";

    @Autowired
    private DormantAccounts dormancy;

    @Autowired
    private FakeGoogle google;

    private long kim;

    @BeforeEach
    void setUp() {
        google.clear();
        kim = createUser(KIM);
        setPassword(kim, PW);
    }

    // ------------------------------------------------------------------ 30일 전 안내

    @Test
    @DisplayName("AC1: 마지막 로그인 1년 되기 30일 전에 안내 메일 DORMANT_NOTICE를 한 번 보낸다 (31일 전은 아직)")
    void noticeThirtyDaysBefore() {
        long lee = createUser("lee@test.local");
        lastLogin(kim, now().minusYears(1).plusDays(30));
        lastLogin(lee, now().minusYears(1).plusDays(31));

        dormancy.run();
        dormancy.run();

        assertThat(mailer.sentTo(KIM, "DORMANT_NOTICE")).hasSize(1);
        assertThat(mailer.sentTo("lee@test.local")).isEmpty();
        assertThat(memberStatus(kim)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("AC1: 한 번도 로그인하지 않은 회원은 가입 시각부터 센다")
    void neverLoggedInCountsFromSignup() {
        jdbc.update("UPDATE users SET last_login_at = NULL, created_at = ? WHERE user_id = ?", now().minusYears(1).plusDays(30), kim);
        dormancy.run();
        assertThat(mailer.sentTo(KIM, "DORMANT_NOTICE")).hasSize(1);
    }

    @Test
    @DisplayName("AC1: 안내를 받은 뒤 로그인하면 다시 1년을 센다 (안내 기록을 지움)")
    void loginResetsNotice() throws Exception {
        lastLogin(kim, now().minusYears(1).plusDays(30));
        dormancy.run();
        loggedIn(KIM, PW, "203.0.113.1");
        assertThat(jdbc.queryForObject("SELECT dormant_notice_sent_at FROM users WHERE user_id = ?", LocalDateTime.class, kim)).isNull();

        clock.advance(Duration.ofDays(31));
        dormancy.run();
        assertThat(memberStatus(kim)).isEqualTo("ACTIVE");
    }

    // ------------------------------------------------------------------ 휴면

    @Test
    @DisplayName("AC1: 1년 지나고 안내 30일 뒤 휴면 — 이메일·이름은 dormant_profiles로(users 칸은 비움), 모든 기기 로그아웃, 남은 메일 링크 무효, 감사 DORMANT(SYSTEM), 작성 데이터는 그대로")
    void becomesDormant() throws Exception {
        Browser b = loggedIn(KIM, PW, "203.0.113.2");
        b.postJson(FORGOT, "{\"email\":\"" + KIM + "\"}").andExpect(status().isAccepted());
        String resetToken = token(mailer.sentTo(KIM, "RESET").getFirst().link());
        int categories = categories(kim);

        makeDormant(kim);

        Map<String, Object> user = jdbc.queryForMap("SELECT email, name, status, dormant_at FROM users WHERE user_id = ?", kim);
        assertThat(user.get("email")).isNull();
        assertThat(user.get("name")).isNull();
        assertThat(user.get("status")).isEqualTo("DORMANT");
        assertThat(user.get("dormant_at")).isEqualTo(now());
        assertThat(jdbc.queryForMap("SELECT email, name FROM dormant_profiles WHERE user_id = ?", kim))
            .containsEntry("email", KIM).containsEntry("name", "회원");
        b.get(ME).andExpect(status().isUnauthorized());
        b.postJson(RESET, "{\"token\":\"" + resetToken + "\",\"newPassword\":\"brand-new-password-678\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(categories(kim)).isEqualTo(categories);
        assertThat(jdbc.queryForList("SELECT CONCAT(actor, ' ', event) FROM audit_logs WHERE user_id = ? AND event = 'DORMANT'", String.class, kim))
            .containsExactly("SYSTEM DORMANT");
    }

    @Test
    @DisplayName("AC1: 안내 메일을 늦게 보냈으면 보낸 지 30일이 지나야 휴면이 된다")
    void dormantOnlyThirtyDaysAfterNotice() {
        lastLogin(kim, now().minusYears(2));
        dormancy.run();
        assertThat(mailer.sentTo(KIM, "DORMANT_NOTICE")).hasSize(1);
        assertThat(memberStatus(kim)).isEqualTo("ACTIVE");

        clock.advance(Duration.ofDays(29));
        dormancy.run();
        assertThat(memberStatus(kim)).isEqualTo("ACTIVE");

        clock.advance(Duration.ofDays(1));
        dormancy.run();
        assertThat(memberStatus(kim)).isEqualTo("DORMANT");
    }

    @Test
    @DisplayName("AC1: 안내 메일 보내기가 실패하면 안내한 것으로 치지 않는다 — 다음 날 다시 보내고, 보낸 날부터 30일 뒤 휴면 (PO #7 1번)")
    void failedNoticeIsRetried() {
        lastLogin(kim, now().minusYears(2));
        mailer.failNext();
        dormancy.run();
        assertThat(mailer.sentTo(KIM, "DORMANT_NOTICE")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT dormant_notice_sent_at FROM users WHERE user_id = ?", LocalDateTime.class, kim)).isNull();

        clock.advance(Duration.ofDays(1));
        dormancy.run();
        assertThat(mailer.sentTo(KIM, "DORMANT_NOTICE")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT dormant_notice_sent_at FROM users WHERE user_id = ?", LocalDateTime.class, kim)).isEqualTo(now());

        clock.advance(Duration.ofDays(29));
        dormancy.run();
        assertThat(memberStatus(kim)).isEqualTo("ACTIVE");
        clock.advance(Duration.ofDays(1));
        dormancy.run();
        assertThat(memberStatus(kim)).isEqualTo("DORMANT");
    }

    @Test
    @DisplayName("AC1: 휴면 대상으로 고른 뒤 바꾸기 전에 로그인하면 휴면이 되지 않는다 — 바꾸기 직전에 조건을 다시 본다 (PO #7 2번)")
    void loginBetweenScanAndConvert() throws Exception {
        jdbc.update("UPDATE users SET last_login_at = ?, dormant_notice_sent_at = ? WHERE user_id = ?", now().minusYears(1).minusDays(1), now().minusDays(30), kim);
        assertThat(dormancy.dueForDormancy(now())).contains(kim);

        loggedIn(KIM, PW, "203.0.113.20");
        assertThat(dormancy.toDormant(kim, now())).isFalse();
        assertThat(memberStatus(kim)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dormant_profiles", Integer.class)).isZero();
    }

    @Test
    @DisplayName("AC1: 반대 순서 — 휴면으로 바꾼 뒤 로그인하면 복원된다 (PO #7 2번)")
    void convertThenLogin() throws Exception {
        jdbc.update("UPDATE users SET last_login_at = ?, dormant_notice_sent_at = ? WHERE user_id = ?", now().minusYears(1).minusDays(1), now().minusDays(30), kim);
        assertThat(dormancy.toDormant(kim, now())).isTrue();
        browser("203.0.113.21").withCsrf().login(KIM, PW).andExpect(status().isOk()).andExpect(jsonPath("$.restored").value(true));
        assertThat(memberStatus(kim)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("AC1: 삭제 대상으로 고른 뒤 삭제 전에 로그인해 복원되면 지우지 않는다 — 지우기 직전에 조건을 다시 본다 (PO #7 3번)")
    void restoredBetweenScanAndDelete() throws Exception {
        makeDormant(kim);
        jdbc.update("UPDATE users SET dormant_at = ? WHERE user_id = ?", now().minusYears(4), kim);
        assertThat(dormancy.dueForDeletion(now())).contains(kim);

        browser("203.0.113.22").withCsrf().login(KIM, PW).andExpect(status().isOk()).andExpect(jsonPath("$.restored").value(true));
        assertThat(dormancy.deleteDormant(kim, now())).isFalse();
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE user_id = ?", String.class, kim)).isEqualTo(KIM);
        assertThat(categories(kim)).isPositive();
    }

    // ------------------------------------------------------------------ 복원

    @Test
    @DisplayName("AC1: 휴면 회원이 비밀번호로 로그인하면 복원 — 200 restored true, 이메일·이름 돌아옴, 감사 DORMANT_RESTORED, 복원 알림 메일. 다음 로그인은 restored false")
    void passwordLoginRestores() throws Exception {
        makeDormant(kim);
        int categories = categories(kim);

        Browser b = browser("203.0.113.3").withCsrf();
        b.login(KIM, PW).andExpect(status().isOk())
            .andExpect(jsonPath("$.restored").value(true))
            .andExpect(jsonPath("$.email").value(KIM))
            .andExpect(jsonPath("$.name").value("회원"));

        Map<String, Object> user = jdbc.queryForMap("SELECT email, name, status, dormant_at, dormant_notice_sent_at FROM users WHERE user_id = ?", kim);
        assertThat(user).containsEntry("email", KIM).containsEntry("name", "회원").containsEntry("status", "ACTIVE");
        assertThat(user.get("dormant_at")).isNull();
        assertThat(user.get("dormant_notice_sent_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dormant_profiles", Integer.class)).isZero();
        assertThat(categories(kim)).isEqualTo(categories);
        assertThat(audits(kim)).containsSubsequence("DORMANT", "DORMANT_RESTORED", "LOGIN");
        assertThat(mailer.sentTo(KIM, "DORMANT_RESTORED")).hasSize(1);
        b.get(ME).andExpect(status().isOk());

        browser("203.0.113.4").withCsrf().login(KIM, PW).andExpect(status().isOk()).andExpect(jsonPath("$.restored").value(false));
    }

    @Test
    @DisplayName("AC1: 휴면 회원 비밀번호가 틀리면 보통 회원과 같은 401 (휴면인지 드러나지 않음), 복원하지 않는다")
    void wrongPasswordDoesNotRestore() throws Exception {
        makeDormant(kim);
        browser("203.0.113.5").withCsrf().login(KIM, "wrong-password-123")
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(memberStatus(kim)).isEqualTo("DORMANT");
    }

    @Test
    @DisplayName("AC1: 휴면 회원이 연결된 구글로 로그인해도 복원된다 — 302 주소에 restored=1")
    void googleLoginRestores() throws Exception {
        GoogleUser hong = new GoogleUser("google-sub-hong", "hong@gmail.com", true, "홍길동");
        googleSignup(hong, "203.0.113.6");
        long id = jdbc.queryForObject("SELECT user_id FROM users WHERE email = 'hong@gmail.com'", Long.class);
        makeDormant(id);

        Browser b = browser("203.0.113.7");
        assertThat(callback(b, start(b), hong)).isEqualTo(FE + "/?restored=1"); // FE가 '휴면에서 돌아왔어요' 안내 (PO #7 4번)
        b.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("hong@gmail.com"));
        assertThat(memberStatus(id)).isEqualTo("ACTIVE");
        assertThat(audits(id)).containsSubsequence("DORMANT", "DORMANT_RESTORED", "LOGIN");
        assertThat(mailer.sentTo("hong@gmail.com", "DORMANT_RESTORED")).hasSize(1);
    }

    @Test
    @DisplayName("AC1: 휴면 회원도 비밀번호 찾기 메일을 받는다(휴면 이메일로) — 재설정만으로는 복원되지 않고, 새 비밀번호로 로그인하면 복원")
    void passwordResetForDormant() throws Exception {
        makeDormant(kim);
        Browser b = browser("203.0.113.8").withCsrf();
        b.postJson(FORGOT, "{\"email\":\"" + KIM + "\"}").andExpect(status().isAccepted());
        assertThat(mailer.sentTo(KIM, "RESET")).hasSize(1);

        String token = token(mailer.sentTo(KIM, "RESET").getFirst().link());
        b.postJson(RESET, "{\"token\":\"" + token + "\",\"newPassword\":\"brand-new-password-678\"}").andExpect(status().isNoContent());
        assertThat(memberStatus(kim)).isEqualTo("DORMANT");
        assertThat(mailer.sentTo(KIM, "PASSWORD_CHANGED")).hasSize(1);

        b.login(KIM, "brand-new-password-678").andExpect(status().isOk()).andExpect(jsonPath("$.restored").value(true));
    }

    // ------------------------------------------------------------------ 같은 이메일

    @Test
    @DisplayName("AC1: 휴면 회원 이메일로 가입·이메일 변경은 409 EMAIL_ALREADY_REGISTERED(loginMethods), 구글 가입은 GOOGLE_EMAIL_ACCOUNT_EXISTS")
    void dormantEmailIsTaken() throws Exception {
        makeDormant(kim);
        Browser b = browser("203.0.113.9").withCsrf();
        b.postJson(SIGNUP, "{\"email\":\"" + KIM + "\",\"password\":\"another-password-123\",\"name\":\"다른 사람\",\"ageOver14\":true}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(jsonPath("$.loginMethods[0]").value("EMAIL"));
        assertThat(mailer.sentTo(KIM, "SIGNUP")).isEmpty();

        long lee = createUser("lee@test.local");
        setPassword(lee, PW);
        Browser l = loggedIn("lee@test.local", PW, "203.0.113.10");
        l.postJson(REAUTH, "{\"password\":\"" + PW + "\"}").andExpect(status().isNoContent());
        l.perform(put(ME + "/email").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + KIM + "\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        Browser g = browser("203.0.113.11");
        assertThat(callback(g, start(g), new GoogleUser("google-sub-other", KIM, true, "김")))
            .isEqualTo(FE + "/login?error=GOOGLE_EMAIL_ACCOUNT_EXISTS");
    }

    // ------------------------------------------------------------------ 정지 (D-069)

    @Test
    @DisplayName("AC1: 정지된 회원도 휴면이 되고, 휴면 중에는 비밀번호가 맞아도 403 ACCOUNT_SUSPENDED로 복원하지 않는다. 정지가 풀리면 로그인해 복원, 정지 칸은 휴면이 건드리지 않는다")
    void suspendedMember() throws Exception {
        LocalDateTime suspendedAt = now().minusDays(400);
        jdbc.update("UPDATE users SET suspended_at = ? WHERE user_id = ?", suspendedAt, kim);
        makeDormant(kim);
        assertThat(memberStatus(kim)).isEqualTo("DORMANT");
        assertThat(jdbc.queryForObject("SELECT suspended_at FROM users WHERE user_id = ?", LocalDateTime.class, kim)).isEqualTo(suspendedAt);

        browser("203.0.113.12").withCsrf().login(KIM, PW)
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        assertThat(memberStatus(kim)).isEqualTo("DORMANT");
        assertThat(mailer.sentTo(KIM, "DORMANT_RESTORED")).isEmpty();

        jdbc.update("UPDATE users SET suspended_at = NULL WHERE user_id = ?", kim);
        browser("203.0.113.13").withCsrf().login(KIM, PW).andExpect(status().isOk()).andExpect(jsonPath("$.restored").value(true));
    }

    // ------------------------------------------------------------------ 4년 뒤 삭제

    @Test
    @DisplayName("AC1: 휴면 4년 뒤 모두 삭제 — 회원·작성 데이터·dormant_profiles, 감사 ACCOUNT_DELETED {reason: DORMANT_EXPIRED}(SYSTEM). 그 이메일로 다시 가입할 수 있다. 4년 하루 전은 그대로")
    void deletedFourYearsAfterDormant() throws Exception {
        long lee = createUser("lee@test.local");
        makeDormant(kim);
        makeDormant(lee);
        jdbc.update("UPDATE users SET dormant_at = ? WHERE user_id = ?", now().minusYears(4), kim);
        jdbc.update("UPDATE users SET dormant_at = ? WHERE user_id = ?", now().minusYears(4).plusDays(1), lee);

        dormancy.run();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ?", Integer.class, kim)).isZero();
        assertThat(categories(kim)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_credentials WHERE user_id = ?", Integer.class, kim)).isZero();
        assertThat(jdbc.queryForList("SELECT email FROM dormant_profiles", String.class)).containsExactly("lee@test.local");
        assertThat(jdbc.queryForList("SELECT CONCAT(actor, ' ', event, ' ', CAST(detail AS CHAR)) FROM audit_logs WHERE user_id = ? AND event = 'ACCOUNT_DELETED'",
            String.class, kim)).containsExactly("SYSTEM ACCOUNT_DELETED {\"reason\": \"DORMANT_EXPIRED\"}");
        assertThat(memberStatus(lee)).isEqualTo("DORMANT");
        assertThat(mailer.sentTo(KIM, "ACCOUNT_DELETED")).isEmpty();

        browser("203.0.113.14").withCsrf()
            .postJson(SIGNUP, "{\"email\":\"" + KIM + "\",\"password\":\"another-password-123\",\"name\":\"새 김\",\"ageOver14\":true}")
            .andExpect(status().isAccepted());
    }

    // ------------------------------------------------------------------ 도우미

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private void lastLogin(long userId, LocalDateTime at) {
        jdbc.update("UPDATE users SET last_login_at = ? WHERE user_id = ?", at, userId);
    }

    /** 1년 넘게 로그인 안 했고 안내도 30일 전에 받은 회원 → 작업을 돌려 휴면으로 */
    private void makeDormant(long userId) {
        jdbc.update("UPDATE users SET last_login_at = ?, dormant_notice_sent_at = ? WHERE user_id = ?",
            now().minusYears(1).minusDays(1), now().minusDays(30), userId);
        dormancy.run();
        assertThat(memberStatus(userId)).isEqualTo("DORMANT");
    }

    private String memberStatus(long userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE user_id = ?", String.class, userId);
    }

    private int categories(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE user_id = ?", Integer.class, userId);
    }

    private List<String> audits(long userId) {
        return jdbc.queryForList("SELECT event FROM audit_logs WHERE user_id = ? ORDER BY audit_id", String.class, userId);
    }

    private static String token(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private Browser googleSignup(GoogleUser user, String ip) throws Exception {
        Browser b = browser(ip);
        assertThat(callback(b, start(b), user)).isEqualTo(FE + "/signup/google");
        b.withCsrf();
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"" + user.name() + "\",\"ageOver14\":true}").andExpect(status().isCreated());
        return b;
    }

    private static String start(Browser b) throws Exception {
        return b.getEncoded(START).andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
    }

    private String callback(Browser b, String authorize, GoogleUser user) throws Exception {
        String code = google.approve(authorize, user, Tamper.NONE);
        String state = FakeGoogle.query(authorize).get("state");
        return b.getEncoded(CALLBACK + "?code=" + enc(code) + "&state=" + enc(state))
            .andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
