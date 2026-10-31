package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.support.FakeGoogle;
import com.awesomedesk.j_planner.support.FakeGoogle.GoogleUser;
import com.awesomedesk.j_planner.support.FakeGoogle.Tamper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

/**
 * US-36 사용자는 구글로 가입·로그인하고, 로그인한 뒤 내 계정에서 구글을 연결한다 (AUTH-02·06·14, D-061).
 * 설계: 08-api-design.md 13-3(/auth/google)·13-4(/me/identities, /me/reauth)·13-5(구글 결과 표)·13-6, 07 11-2(user_identities).
 * <p>
 * 구글은 가짜({@link FakeGoogle}, 127.0.0.1)다. 테스트가 우리 서버의 302(구글 로그인 화면 주소)를 받아 '구글에서 로그인'을 흉내 내고
 * code·state를 들고 콜백으로 돌아온다. 가짜 구글의 토큰 주소는 redirect_uri·PKCE·클라이언트 비밀값을 진짜처럼 확인한다.
 * <p>
 * FE 담당: 로그인·가입 화면의 [구글로 계속하기](구글 버튼 가이드, US-39), 콜백 결과 화면(/login?error=…, /me?linked=google, /me?error=…) 문구,
 * 내 계정 화면의 연결·해제 버튼, 작성 중 '다시 로그인' 창의 [Google로 계속하기](팝업 → /login/google-done → 같은 계정인지 GET /me로 확인 → 이어서 저장).
 */
@DisplayName("US-36 구글 가입·로그인·연결")
class US36GoogleLoginAcceptanceTest extends BrowserAcceptanceTest {

    private static final String START = "/api/v1/auth/google";
    private static final String CALLBACK = "/api/v1/auth/google/callback";
    private static final String CONNECT = "/api/v1/me/identities/google/connect";
    private static final String IDENTITIES = "/api/v1/me/identities";
    private static final String DISCONNECT = "/api/v1/me/identities/google";
    private static final String REAUTH = "/api/v1/me/reauth";
    private static final String SIGNUP_GOOGLE = "/api/v1/auth/google/signup";
    private static final String ME = "/api/v1/me";
    private static final String FE = "http://localhost:3000";
    private static final String REDIRECT_URI = FE + CALLBACK;

    private static final GoogleUser HONG = new GoogleUser("google-sub-hong", "hong@gmail.com", true, "홍길동");

    @Autowired
    private FakeGoogle google;

    @BeforeEach
    void clearGoogle() {
        google.clear();
    }

    // ------------------------------------------------------------------ AC1 OpenID Connect

    @Test
    @DisplayName("AC1: 시작하면 OpenID Connect(scope openid email profile)·state·nonce·PKCE(S256)·설정한 redirect URI로 구글에 보낸다 (시작할 때마다 새 값)")
    void startSendsOidcRequest() throws Exception {
        Browser b = browser("198.51.100.1");
        String first = start(b, START);
        String second = start(browser("198.51.100.2"), START);

        assertThat(first).startsWith(google.authorizationUri() + "?");
        Map<String, String> q = FakeGoogle.query(first);
        assertThat(q.get("response_type")).isEqualTo("code");
        assertThat(q.get("client_id")).isEqualTo(FakeGoogle.CLIENT_ID);
        assertThat(Set.of(q.get("scope").split(" "))).containsExactlyInAnyOrder("openid", "email", "profile");
        assertThat(q.get("redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(q.get("code_challenge_method")).isEqualTo("S256");
        assertThat(q.get("code_challenge")).hasSize(43);
        assertThat(q.get("state")).isNotBlank();
        assertThat(q.get("nonce")).isNotBlank();

        Map<String, String> q2 = FakeGoogle.query(second);
        assertThat(q2.get("state")).isNotEqualTo(q.get("state"));
        assertThat(q2.get("nonce")).isNotEqualTo(q.get("nonce"));
        assertThat(q2.get("code_challenge")).isNotEqualTo(q.get("code_challenge"));
    }

    @Test
    @DisplayName("AC1: 로그인 전에 만든 세션(구글 왕복용)은 10분만 유지된다 — 구글 시작만 반복해도 세션이 오래 쌓이지 않게")
    void preLoginSessionIsShort() throws Exception {
        Browser b = browser("198.51.100.3");
        start(b, START);
        assertThat(b.sid).isNotNull();
        Long ttl = redis.getExpire("jp:session:sessions:" + sessionId(b));
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(15).toSeconds());
    }

    @Test
    @DisplayName("AC1: 콜백은 같은 redirect URI와 PKCE 검증값(code_verifier)으로 토큰을 받는다")
    void tokenRequestUsesPkceAndSameRedirectUri() throws Exception {
        Browser b = browser("198.51.100.4");
        String authorize = start(b, START);
        String location = callback(b, authorize, HONG, Tamper.NONE);
        assertThat(location).isEqualTo(FE + "/signup/google");

        FakeGoogle.TokenRequest token = google.tokenRequests().getLast();
        assertThat(token.form().get("redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(FakeGoogle.pkceMatches(token.form().get("code_verifier"), FakeGoogle.query(authorize).get("code_challenge"))).isTrue();
    }

    @Test
    @DisplayName("AC1: state가 다르거나 시작한 브라우저가 아니면 로그인하지 않는다 → /login?error=GOOGLE_FAILED")
    void stateMustMatch() throws Exception {
        Browser b = browser("198.51.100.5");
        String authorize = start(b, START);
        String code = google.approve(authorize, HONG);

        assertThat(location(b.getEncoded(CALLBACK + "?code=" + enc(code) + "&state=wrong-state"))).isEqualTo(FE + "/login?error=GOOGLE_FAILED");

        Browser other = browser("198.51.100.6");
        String code2 = google.approve(authorize, HONG);
        String state = FakeGoogle.query(authorize).get("state");
        assertThat(location(other.getEncoded(CALLBACK + "?code=" + enc(code2) + "&state=" + enc(state)))).isEqualTo(FE + "/login?error=GOOGLE_FAILED");

        assertThat(userCount()).isEqualTo(1);
        b.get(ME).andExpect(status().isUnauthorized());
        other.get(ME).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC1: ID 토큰이 위조·다른 앱(aud)·다른 발급자(iss)·만료·nonce 불일치면 로그인하지 않는다 → /login?error=GOOGLE_FAILED")
    void idTokenIsValidated() throws Exception {
        int i = 10;
        for (Tamper tamper : List.of(Tamper.BAD_SIGNATURE, Tamper.WRONG_AUDIENCE, Tamper.WRONG_ISSUER, Tamper.EXPIRED, Tamper.WRONG_NONCE)) {
            Browser b = browser("198.51.100." + i++);
            assertThat(callback(b, start(b, START), HONG, tamper)).as(tamper.name()).isEqualTo(FE + "/login?error=GOOGLE_FAILED");
            b.get(ME).andExpect(status().isUnauthorized());
        }
        assertThat(userCount()).isEqualTo(1);
        assertThat(identityCount()).isZero();
    }

    @Test
    @DisplayName("AC1: 구글에서 취소하거나 오류가 오면 → /login?error=GOOGLE_FAILED")
    void cancelledAtGoogle() throws Exception {
        Browser b = browser("198.51.100.20");
        String state = FakeGoogle.query(start(b, START)).get("state");
        assertThat(location(b.getEncoded(CALLBACK + "?error=access_denied&state=" + enc(state)))).isEqualTo(FE + "/login?error=GOOGLE_FAILED");
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC1: 처음 보는 구글은 email_verified=true일 때만 새 회원 — false면 /login?error=GOOGLE_FAILED, 회원을 만들지 않는다")
    void newMemberOnlyWhenEmailVerified() throws Exception {
        Browser b = browser("198.51.100.21");
        GoogleUser unverified = new GoogleUser("google-sub-unverified", "unverified@gmail.com", false, "미인증");
        assertThat(callback(b, start(b, START), unverified, Tamper.NONE)).isEqualTo(FE + "/login?error=GOOGLE_FAILED");
        assertThat(userCount()).isEqualTo(1);
        assertThat(pendingOrUser("unverified@gmail.com")).isZero();
    }

    @Test
    @DisplayName("D-083: 처음 보는 구글(email_verified=true)은 바로 가입하지 않고 확인 화면 /signup/google — 회원 없음, GET은 구글 이메일·채워 둘 이름")
    void newGoogleGoesToConfirmation() throws Exception {
        Browser b = browser("198.51.100.22");
        assertThat(callback(b, start(b, START), new GoogleUser("google-sub-new", "  New.Member@Gmail.com ", true, "  새 회원  "), Tamper.NONE))
            .isEqualTo(FE + "/signup/google");
        assertThat(userCount()).isEqualTo(1);
        assertThat(identityCount()).isZero();
        b.get(ME).andExpect(status().isUnauthorized());

        b.get(SIGNUP_GOOGLE).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("new.member@gmail.com"))
            .andExpect(jsonPath("$.name").value("새 회원"));
    }

    @Test
    @DisplayName("D-083: [가입하기] → 201 새 회원 + 구글 연결 + 기본 데이터 + 로그인(새 세션 ID), 감사 기록 SIGNUP_COMPLETED·LOGIN {method: GOOGLE}, 대기 정보는 지워진다")
    void googleSignup() throws Exception {
        Browser b = browser("198.51.100.62");
        assertThat(callback(b, start(b, START), new GoogleUser("google-sub-new", "new.member@gmail.com", true, "새 회원"), Tamper.NONE))
            .isEqualTo(FE + "/signup/google");
        String before = b.sid.getValue();
        b.withCsrf();

        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"  바꾼 이름 \",\"ageOver14\":true}").andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("new.member@gmail.com"))
            .andExpect(jsonPath("$.name").value("바꾼 이름"))
            .andExpect(jsonPath("$.hasPassword").value(false))
            .andExpect(jsonPath("$.loginMethods").value(org.hamcrest.Matchers.contains("GOOGLE")));
        assertThat(b.sid).isNotNull();
        assertThat(b.sid.getValue()).isNotEqualTo(before);
        b.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("바꾼 이름"));

        long userId = userId("new.member@gmail.com");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE user_id = ? AND is_default = 'Y'", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_settings WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_credentials WHERE user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM users WHERE user_id = ?", Boolean.class, userId)).isTrue();
        assertThat(jdbc.queryForMap("SELECT provider, provider_subject, email_at_link FROM user_identities WHERE user_id = ?", userId))
            .containsEntry("provider", "GOOGLE").containsEntry("provider_subject", "google-sub-new").containsEntry("email_at_link", "new.member@gmail.com");
        assertThat(audits(userId)).contains("SIGNUP_COMPLETED {\"method\": \"GOOGLE\"}", "LOGIN {\"method\": \"GOOGLE\"}");

        b.get(SIGNUP_GOOGLE).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOOGLE_SIGNUP_EXPIRED"));
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"또\",\"ageOver14\":true}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("GOOGLE_SIGNUP_EXPIRED"));
        assertThat(userCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("D-083: 이름 1~50자(앞뒤 공백 정리)·만 14세 이상 체크가 없으면 400 VALIDATION_FAILED (errors name·ageOver14), 회원 안 만듦")
    void signupValidation() throws Exception {
        Browser b = browser("198.51.100.63");
        callback(b, start(b, START), HONG, Tamper.NONE);
        b.withCsrf();
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"   \",\"ageOver14\":false}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.containsInAnyOrder("name", "ageOver14")));
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"" + "가".repeat(51) + "\",\"ageOver14\":true}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("name"));
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"홍길동\"}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("ageOver14"));
        assertThat(userCount()).isEqualTo(1);
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"" + "가".repeat(50) + "\",\"ageOver14\":true}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("D-083: 가입 대기는 10분 — 지나거나, 대기가 없거나, 다른 브라우저면 400 GOOGLE_SIGNUP_EXPIRED")
    void pendingExpires() throws Exception {
        browser("198.51.100.64").get(SIGNUP_GOOGLE).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOOGLE_SIGNUP_EXPIRED"));
        browser("198.51.100.64").withCsrf().postJson(SIGNUP_GOOGLE, "{\"name\":\"홍\",\"ageOver14\":true}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOOGLE_SIGNUP_EXPIRED"));

        Browser b = browser("198.51.100.65");
        callback(b, start(b, START), HONG, Tamper.NONE);
        b.withCsrf();
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        b.get(SIGNUP_GOOGLE).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GOOGLE_SIGNUP_EXPIRED"));
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"홍\",\"ageOver14\":true}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("GOOGLE_SIGNUP_EXPIRED"));
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("D-083: 기다리는 사이 같은 이메일 회원이 생기면 409 EMAIL_ALREADY_REGISTERED(loginMethods), 같은 구글이 다른 창에서 먼저 가입했으면 400 GOOGLE_SIGNUP_EXPIRED")
    void raceWhileWaiting() throws Exception {
        Browser b = browser("198.51.100.66");
        callback(b, start(b, START), HONG, Tamper.NONE);
        b.withCsrf();
        long other = createUser("hong@gmail.com");
        setPassword(other, "member-password-123");
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"홍\",\"ageOver14\":true}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(jsonPath("$.loginMethods").value(org.hamcrest.Matchers.contains("EMAIL")));
        assertThat(identityCount()).isZero();

        GoogleUser kim = new GoogleUser("google-sub-kim", "kim@gmail.com", true, "김");
        Browser first = browser("198.51.100.67");
        Browser second = browser("198.51.100.68");
        callback(first, start(first, START), kim, Tamper.NONE);
        callback(second, start(second, START), kim, Tamper.NONE);
        first.withCsrf().postJson(SIGNUP_GOOGLE, "{\"name\":\"김\",\"ageOver14\":true}").andExpect(status().isCreated());
        second.withCsrf().postJson(SIGNUP_GOOGLE, "{\"name\":\"김\",\"ageOver14\":true}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("GOOGLE_SIGNUP_EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'kim@gmail.com'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("D-083: 구글 시작에 next가 있었으면 확인 화면 GET이 next를 알려 준다 (가입 뒤 FE가 그리로)")
    void signupKeepsNext() throws Exception {
        Browser b = browser("198.51.100.69");
        assertThat(callback(b, start(b, START + "?next=" + enc("/?view=week")), HONG, Tamper.NONE)).isEqualTo(FE + "/signup/google");
        b.get(SIGNUP_GOOGLE).andExpect(jsonPath("$.next").value("/?view=week"));
        Browser c = browser("198.51.100.70");
        callback(c, start(c, START), new GoogleUser("google-sub-c", "c@gmail.com", true, "씨"), Tamper.NONE);
        c.get(SIGNUP_GOOGLE).andExpect(jsonPath("$.next").doesNotExist());
    }

    @Test
    @DisplayName("D-083: 다시 로그인 팝업(popup=true)에서 처음 보는 구글이면 가입하지 않고 /login/google-done?error=GOOGLE_NOT_REGISTERED (FE 질문 A안)")
    void popupWithNewGoogle() throws Exception {
        Browser b = browser("198.51.100.71");
        assertThat(callback(b, start(b, START + "?popup=true"), HONG, Tamper.NONE)).isEqualTo(FE + "/login/google-done?error=GOOGLE_NOT_REGISTERED");
        b.get(SIGNUP_GOOGLE).andExpect(status().isBadRequest());
        assertThat(userCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("D-082 1번·D-083: 확인 화면에 채워 둘 이름 = 구글 이름, 없으면 이메일 @ 앞부분, 50자가 넘으면 50자까지")
    void nameFallback() throws Exception {
        Browser b = browser("198.51.100.23");
        callback(b, start(b, START), new GoogleUser("google-sub-noname", "noname.user@gmail.com", true, null), Tamper.NONE);
        b.get(SIGNUP_GOOGLE).andExpect(jsonPath("$.name").value("noname.user"));

        Browser c = browser("198.51.100.24");
        callback(c, start(c, START), new GoogleUser("google-sub-long", "long@gmail.com", true, "가".repeat(60)), Tamper.NONE);
        c.get(SIGNUP_GOOGLE).andExpect(jsonPath("$.name").value("가".repeat(50)));
    }

    @Test
    @DisplayName("AC1: 사용자는 구글 sub로 구분한다 — 구글 이메일이 바뀌어도 같은 계정으로 로그인 (회원 이메일은 그대로)")
    void identifiedBySub() throws Exception {
        googleSignup("198.51.100.25", HONG);
        long userId = userId("hong@gmail.com");

        Browser other = browser("198.51.100.26");
        GoogleUser emailChanged = new GoogleUser(HONG.sub(), "gildong.new@gmail.com", true, "홍길동");
        assertThat(callback(other, start(other, START), emailChanged, Tamper.NONE)).isEqualTo(FE + "/");
        other.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("hong@gmail.com"));

        assertThat(userCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND event = 'LOGIN'", Integer.class, userId)).isEqualTo(2);
    }

    @Test
    @DisplayName("D-069: 정지된 회원은 구글로도 로그인할 수 없다 → /login?error=ACCOUNT_SUSPENDED, 감사 기록 LOGIN_FAILED {method: GOOGLE, reason: SUSPENDED}")
    void suspendedMember() throws Exception {
        googleSignup("198.51.100.27", HONG);
        long userId = userId("hong@gmail.com");
        jdbc.update("UPDATE users SET suspended_at = NOW() WHERE user_id = ?", userId);

        Browser other = browser("198.51.100.28");
        assertThat(callback(other, start(other, START), HONG, Tamper.NONE)).isEqualTo(FE + "/login?error=ACCOUNT_SUSPENDED");
        other.get(ME).andExpect(status().isUnauthorized());
        assertThat(audits(userId)).contains("LOGIN_FAILED {\"method\": \"GOOGLE\", \"reason\": \"SUSPENDED\"}");
    }

    @Test
    @DisplayName("로그인 뒤 갈 곳(next)은 사이트 안 경로만 따른다 — 바깥 주소·//·\\ 는 / 로, 실패하면 /login?error=…&next=…")
    void nextIsSiteRelativeOnly() throws Exception {
        googleSignup("198.51.100.28", HONG);
        Browser b = browser("198.51.100.29");
        assertThat(callback(b, start(b, START + "?next=" + enc("/?view=week")), HONG, Tamper.NONE)).isEqualTo(FE + "/?view=week");

        int i = 30;
        for (String bad : List.of("//evil.example.com", "https://evil.example.com", "/\\evil.example.com", "javascript:alert(1)")) {
            Browser c = browser("198.51.100." + i++);
            assertThat(callback(c, start(c, START + "?next=" + enc(bad)), HONG, Tamper.NONE)).as(bad).isEqualTo(FE + "/");
        }

        Browser d = browser("198.51.100.40");
        assertThat(callback(d, start(d, START + "?next=" + enc("/?view=week")), HONG, Tamper.EXPIRED))
            .isEqualTo(FE + "/login?error=GOOGLE_FAILED&next=" + enc("/?view=week"));
    }

    // ------------------------------------------------------------------ AC2 같은 이메일

    @Test
    @DisplayName("AC2: 같은 이메일의 이메일 가입 계정이 있으면 새 계정 없이 /login?error=GOOGLE_EMAIL_ACCOUNT_EXISTS (대소문자 무시, 비밀번호 없는 admin도)")
    void emailAccountExists() throws Exception {
        long member = createUser("hong@gmail.com");
        setPassword(member, "member-password-123");

        Browser b = browser("198.51.100.41");
        GoogleUser upper = new GoogleUser("google-sub-hong", "Hong@Gmail.COM", true, "홍길동");
        assertThat(callback(b, start(b, START), upper, Tamper.NONE)).isEqualTo(FE + "/login?error=GOOGLE_EMAIL_ACCOUNT_EXISTS");
        b.get(ME).andExpect(status().isUnauthorized());

        Browser c = browser("198.51.100.42");
        GoogleUser admin = new GoogleUser("google-sub-admin", "admin@test.local", true, "관리자");
        assertThat(callback(c, start(c, START), admin, Tamper.NONE)).isEqualTo(FE + "/login?error=GOOGLE_EMAIL_ACCOUNT_EXISTS");

        assertThat(userCount()).isEqualTo(2);
        assertThat(identityCount()).isZero();
    }

    @Test
    @DisplayName("AC2: 임시 가입 중인 이메일이면 임시 가입을 지우고 구글로 가입한다 (이전 인증 링크는 무효)")
    void pendingSignupReplaced() throws Exception {
        Browser b = browser("198.51.100.43").withCsrf();
        b.postJson("/api/v1/auth/signup", "{\"email\":\"hong@gmail.com\",\"password\":\"pending-password-123\",\"name\":\"임시\",\"ageOver14\":true}")
            .andExpect(status().isAccepted());
        String link = mailer.sentTo("hong@gmail.com", "SIGNUP").getLast().link();
        String token = link.substring(link.indexOf("token=") + "token=".length());

        assertThat(callback(b, start(b, START), HONG, Tamper.NONE)).isEqualTo(FE + "/signup/google");
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"홍길동\",\"ageOver14\":true}").andExpect(status().isCreated());
        b.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("hong@gmail.com")).andExpect(jsonPath("$.hasPassword").value(false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_signups WHERE email = 'hong@gmail.com'", Integer.class)).isZero();

        browser("198.51.100.44").withCsrf().postJson("/api/v1/auth/signup/verify", "{\"token\":\"" + token + "\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(userCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC2: 가입 409의 loginMethods — 구글로 가입한 계정은 GOOGLE만, 이메일 계정에 구글을 연결하면 EMAIL·GOOGLE")
    void signupConflictShowsGoogle() throws Exception {
        googleSignup("198.51.100.45", HONG);

        Browser b = browser("198.51.100.46").withCsrf();
        b.postJson("/api/v1/auth/signup", "{\"email\":\"hong@gmail.com\",\"password\":\"another-password-123\",\"name\":\"홍\",\"ageOver14\":true}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.loginMethods").value(org.hamcrest.Matchers.contains("GOOGLE")));

        Browser e = emailMemberWithGoogle("kim@test.local", new GoogleUser("google-sub-kim", "kim.personal@gmail.com", true, "김"));
        e.get(ME).andExpect(jsonPath("$.loginMethods").value(org.hamcrest.Matchers.contains("EMAIL", "GOOGLE")));
        b.postJson("/api/v1/auth/signup", "{\"email\":\"kim@test.local\",\"password\":\"another-password-123\",\"name\":\"김\",\"ageOver14\":true}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.loginMethods").value(org.hamcrest.Matchers.contains("EMAIL", "GOOGLE")));
    }

    // ------------------------------------------------------------------ AC3 연결은 내 계정에서만

    @Test
    @DisplayName("AC3: 구글 연결은 로그인한 뒤(401) 재인증 5분 안에만(403 REAUTH_REQUIRED) 시작된다. 재인증 비밀번호가 틀리면 400 REAUTH_FAILED")
    void connectNeedsLoginAndReauth() throws Exception {
        browser("198.51.100.50").get(CONNECT).andExpect(status().isUnauthorized());

        long member = createUser("kim@test.local");
        setPassword(member, "member-password-123");
        Browser b = loggedIn("kim@test.local", "member-password-123", "198.51.100.51");

        b.get(CONNECT).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        b.postJson(REAUTH, "{\"password\":\"wrong-password-123\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REAUTH_FAILED"));
        b.get(ME).andExpect(status().isOk());
        b.get(CONNECT).andExpect(status().isForbidden());

        b.postJson(REAUTH, "{\"password\":\"member-password-123\"}").andExpect(status().isNoContent());
        String authorize = start(b, CONNECT);
        assertThat(authorize).startsWith(google.authorizationUri() + "?");
        Map<String, String> q = FakeGoogle.query(authorize);
        assertThat(q.get("redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(q.get("code_challenge_method")).isEqualTo("S256");
        assertThat(q.get("prompt")).isEqualTo("select_account");

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        b.get(CONNECT).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
    }

    @Test
    @DisplayName("AC3: 연결하면 /me?linked=google + 알림 메일 + 감사 기록 IDENTITY_LINKED, 이후 이메일·구글 둘 다 같은 계정으로 로그인 (합치기 없음)")
    void connectGoogle() throws Exception {
        long member = createUser("kim@test.local");
        setPassword(member, "member-password-123");
        GoogleUser kimGoogle = new GoogleUser("google-sub-kim", "kim.personal@gmail.com", true, "김구글");
        Browser b = loggedIn("kim@test.local", "member-password-123", "198.51.100.52");
        b.postJson(REAUTH, "{\"password\":\"member-password-123\"}").andExpect(status().isNoContent());

        assertThat(callback(b, start(b, CONNECT), kimGoogle, Tamper.NONE)).isEqualTo(FE + "/me?linked=google");

        b.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("kim@test.local"));
        b.get(IDENTITIES).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].provider").value("GOOGLE"))
            .andExpect(jsonPath("$[0].email").value("kim.personal@gmail.com"))
            .andExpect(jsonPath("$[0].linkedAt").value("2026-09-25T09:00:00"));
        assertThat(mailer.sentTo("kim@test.local", "GOOGLE_LINKED")).hasSize(1);
        assertThat(audits(member)).contains("IDENTITY_LINKED {\"provider\": \"GOOGLE\"}");

        Browser other = browser("198.51.100.53");
        assertThat(callback(other, start(other, START), kimGoogle, Tamper.NONE)).isEqualTo(FE + "/");
        other.get(ME).andExpect(jsonPath("$.email").value("kim@test.local"));
        loggedIn("kim@test.local", "member-password-123", "198.51.100.54").get(ME).andExpect(status().isOk());
        assertThat(userCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC3: 다른 계정에 연결된 구글은 거절 → /me?error=GOOGLE_ALREADY_LINKED (연결·메일 없음)")
    void googleLinkedToAnotherAccount() throws Exception {
        googleSignup("198.51.100.55", HONG);

        long member = createUser("kim@test.local");
        setPassword(member, "member-password-123");
        Browser b = loggedIn("kim@test.local", "member-password-123", "198.51.100.56");
        b.postJson(REAUTH, "{\"password\":\"member-password-123\"}").andExpect(status().isNoContent());

        assertThat(callback(b, start(b, CONNECT), HONG, Tamper.NONE)).isEqualTo(FE + "/me?error=GOOGLE_ALREADY_LINKED");
        b.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("kim@test.local"));
        b.get(IDENTITIES).andExpect(jsonPath("$.length()").value(0));
        assertThat(mailer.sentTo("kim@test.local")).isEmpty();
    }

    @Test
    @DisplayName("AC3: 연결 중 구글에서 취소·토큰 오류 → /me?error=GOOGLE_FAILED, 로그인은 그대로")
    void connectFailed() throws Exception {
        long member = createUser("kim@test.local");
        setPassword(member, "member-password-123");
        Browser b = loggedIn("kim@test.local", "member-password-123", "198.51.100.57");
        b.postJson(REAUTH, "{\"password\":\"member-password-123\"}").andExpect(status().isNoContent());

        String state = FakeGoogle.query(start(b, CONNECT)).get("state");
        assertThat(location(b.getEncoded(CALLBACK + "?error=access_denied&state=" + enc(state)))).isEqualTo(FE + "/me?error=GOOGLE_FAILED");
        assertThat(callback(b, start(b, CONNECT), new GoogleUser("google-sub-kim", "k@gmail.com", true, "김"), Tamper.WRONG_NONCE))
            .isEqualTo(FE + "/me?error=GOOGLE_FAILED");
        b.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("kim@test.local"));
        assertThat(identityCount()).isZero();
    }

    @Test
    @DisplayName("AC3: 이미 구글이 연결돼 있으면 연결을 다시 시작할 수 없다 → 409 IDENTITY_ALREADY_CONNECTED (계정당 구글 하나)")
    void alreadyConnected() throws Exception {
        Browser e = emailMemberWithGoogle("kim@test.local", new GoogleUser("google-sub-kim", "kim.personal@gmail.com", true, "김"));
        e.postJson(REAUTH, "{\"password\":\"member-password-123\"}").andExpect(status().isNoContent());
        e.get(CONNECT).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_CONNECTED"));
    }

    @Test
    @DisplayName("AC3: 연결 해제 — 연결 없으면 404, 마지막 로그인 수단(비밀번호 없음)이면 409 LAST_IDENTITY, 재인증 5분 지나면 403, 해제하면 204 + 알림 메일 + IDENTITY_UNLINKED")
    void disconnectGoogle() throws Exception {
        Browser g = googleSignup("198.51.100.58", HONG);
        g.perform(delete(DISCONNECT)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_IDENTITY"));
        g.get(IDENTITIES).andExpect(jsonPath("$.length()").value(1));

        long member = createUser("kim@test.local");
        setPassword(member, "member-password-123");
        Browser plain = loggedIn("kim@test.local", "member-password-123", "198.51.100.59");
        plain.get(IDENTITIES).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        plain.perform(delete(DISCONNECT)).andExpect(status().isNotFound());

        Browser e = emailMemberWithGoogle("lee@test.local", new GoogleUser("google-sub-lee", "lee.personal@gmail.com", true, "이"));
        long lee = userId("lee@test.local");
        clock.advance(Duration.ofMinutes(6));
        e.perform(delete(DISCONNECT)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        e.postJson(REAUTH, "{\"password\":\"member-password-123\"}").andExpect(status().isNoContent());
        e.perform(delete(DISCONNECT)).andExpect(status().isNoContent());

        e.get(IDENTITIES).andExpect(jsonPath("$.length()").value(0));
        e.get(ME).andExpect(jsonPath("$.loginMethods").value(org.hamcrest.Matchers.contains("EMAIL")));
        assertThat(mailer.sentTo("lee@test.local", "GOOGLE_UNLINKED")).hasSize(1);
        assertThat(audits(lee)).contains("IDENTITY_UNLINKED {\"provider\": \"GOOGLE\"}");
    }

    // ------------------------------------------------------------------ AC4 다시 로그인 창

    @Test
    @DisplayName("AC4: 다시 로그인 창의 [Google로 계속하기] — popup=true면 결과를 /login/google-done(실패면 ?error=)로 보내고, 같은 계정이면 쓰던 저장을 이어서 할 수 있다")
    void popupReLogin() throws Exception {
        Browser b = googleSignup("198.51.100.60", HONG);

        endAllSessions(); // 작성 중 로그인 만료
        String category = "{\"name\":\"이어서 저장\"}";
        b.postJson("/api/v1/categories", category).andExpect(status().isUnauthorized());

        assertThat(callback(b, start(b, START + "?popup=true"), HONG, Tamper.NONE)).isEqualTo(FE + "/login/google-done");
        b.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("hong@gmail.com"));
        b.postJson("/api/v1/categories", category).andExpect(status().isCreated());

        Browser c = browser("198.51.100.61");
        assertThat(callback(c, start(c, START + "?popup=true"), HONG, Tamper.EXPIRED)).isEqualTo(FE + "/login/google-done?error=GOOGLE_FAILED");
    }

    // ------------------------------------------------------------------ 도우미

    /** 구글로 가입 (D-083: 콜백 → 확인 화면 /signup/google → 가입하기) — 로그인되고 CSRF 쿠키가 있는 브라우저 */
    private Browser googleSignup(String ip, GoogleUser user) throws Exception {
        Browser b = browser(ip);
        assertThat(callback(b, start(b, START), user, Tamper.NONE)).isEqualTo(FE + "/signup/google");
        b.withCsrf();
        b.postJson(SIGNUP_GOOGLE, "{\"name\":\"" + user.name() + "\",\"ageOver14\":true}").andExpect(status().isCreated());
        return b;
    }

    /** 시작 주소를 열면 구글 로그인 화면으로 302 — 그 주소 */
    private static String start(Browser b, String url) throws Exception {
        String location = b.getEncoded(url).andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        return location;
    }

    /** 구글에서 로그인·동의하고 콜백으로 돌아온다 — 콜백이 보낸 FE 주소 */
    private String callback(Browser b, String authorize, GoogleUser user, Tamper tamper) throws Exception {
        String code = google.approve(authorize, user, tamper);
        String state = FakeGoogle.query(authorize).get("state");
        return location(b.getEncoded(CALLBACK + "?code=" + enc(code) + "&state=" + enc(state)));
    }

    private static String location(ResultActions result) throws Exception {
        return result.andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
    }

    /** 비밀번호로 로그인한 이메일 회원 + 재인증 + 구글 연결까지 */
    private Browser emailMemberWithGoogle(String email, GoogleUser googleUser) throws Exception {
        long member = createUser(email);
        setPassword(member, "member-password-123");
        Browser b = loggedIn(email, "member-password-123", "198.51.100." + (100 + (int) member));
        b.postJson(REAUTH, "{\"password\":\"member-password-123\"}").andExpect(status().isNoContent());
        assertThat(callback(b, start(b, CONNECT), googleUser, Tamper.NONE)).isEqualTo(FE + "/me?linked=google");
        return b;
    }

    /** 세션이 모두 끝난 것처럼 (Redis의 세션 키를 지움) */
    private void endAllSessions() {
        for (String key : redis.keys("jp:session:*")) {
            redis.delete(key);
        }
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

    private int identityCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_identities", Integer.class);
    }

    private int pendingOrUser(String email) {
        return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM users WHERE email = ?) + (SELECT COUNT(*) FROM pending_signups WHERE email = ?)",
            Integer.class, email, email);
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email);
    }

    /** 그 회원의 감사 기록 "EVENT detail" */
    private List<String> audits(long userId) {
        return jdbc.queryForList("SELECT CONCAT(event, IFNULL(CONCAT(' ', CAST(detail AS CHAR)), '')) FROM audit_logs WHERE user_id = ? ORDER BY audit_id",
            String.class, userId);
    }
}
