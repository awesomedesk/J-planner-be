package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.awesomedesk.j_planner.support.OpenApiContract;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * US-34 사용자는 이메일로 로그인·로그아웃한다 (AUTH-05·08, D-061).
 * 설계: 08-api-design.md 13-1(로그인 유지)·13-3(/auth)·13-4(/me)·13-6(오류 코드)·13-7(요청 횟수 제한), 07 11절.
 * <p>
 * 실제 브라우저처럼 쿠키(세션 __Host-JP_SID, CSRF __Host-XSRF-TOKEN)를 주고받는 {@link Browser}로 확인한다.
 * 기본 로그인·CSRF가 붙은 {@code mvc} 대신 기본값 없는 MockMvc(세션 필터 + Spring Security + 명세 대조)를 쓴다.
 * <p>
 * '없는 계정도 같은 응답 시간'은 시간을 재면 테스트가 흔들려서, 없는 계정도 비밀번호 해시 비교를 똑같이 한 번 하는지를
 * 단위 테스트(PasswordVerifierTest)로 확인한다. 늦추는 시간표(5·10·20회)는 LoginBackoffTest.
 * FE 담당: 로그인 화면, 앱을 열 때 GET /auth/csrf, 401이면 로그인 화면으로.
 */
@DisplayName("US-34 이메일 로그인·로그아웃")
class US34LoginAcceptanceTest extends AcceptanceTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String CSRF = "/api/v1/auth/csrf";
    private static final String ME = "/api/v1/me";
    private static final String SESSION_COOKIE = "__Host-JP_SID";
    private static final String XSRF_COOKIE = "__Host-XSRF-TOKEN";
    private static final String FAIL = "이메일 또는 비밀번호가 맞지 않아요";

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "admin-password-1234";
    private static final String OTHER_EMAIL = "other@test.local";
    private static final String OTHER_PASSWORD = "other-password-5678";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter sessionFilter;

    private MockMvc web;
    private long other;

    @BeforeEach
    void setUp() {
        web = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(sessionFilter)
            .apply(springSecurity())
            .alwaysExpect(OpenApiValidationMatchers.openApi().isValid(OpenApiContract.validator()))
            .build();
        setPassword(ADMIN_ID, ADMIN_PASSWORD);
        other = createUser(OTHER_EMAIL);
        setPassword(other, OTHER_PASSWORD);
    }

    // ------------------------------------------------------------------ AC1 실패·늦추기·IP 제한

    @Test
    @DisplayName("AC1: 틀린 비밀번호·없는 계정·비밀번호 없는 계정 모두 같은 401 문구 하나다")
    void oneFailureMessage() throws Exception {
        long noPassword = createUser("nopass@test.local");
        assertThat(noPassword).isPositive();

        for (String[] attempt : List.of(
            new String[]{ADMIN_EMAIL, "wrong-password-0000"},
            new String[]{"nobody@test.local", ADMIN_PASSWORD},
            new String[]{"nopass@test.local", "any-password-1234"})) {
            new Browser("198.51.100.1").withCsrf().login(attempt[0], attempt[1])
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value(FAIL));
        }
    }

    @Test
    @DisplayName("AC1: 같은 계정으로 5번 틀리면 잠시(30초) 막히고(맞는 비밀번호도 429 + 같은 문구), 기다리면 다시 되며 성공하면 횟수가 초기화된다 (영구 잠금 없음)")
    void slowsDownAfterFailures() throws Exception {
        Browser b = new Browser("198.51.100.2").withCsrf();
        for (int i = 0; i < 5; i++) {
            b.login(ADMIN_EMAIL, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        MvcResult blocked = b.login(ADMIN_EMAIL, ADMIN_PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
            .andExpect(jsonPath("$.detail").value(FAIL))
            .andReturn();
        int retryAfter = Integer.parseInt(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isBetween(1, 30);

        // 30초가 지난 것처럼 (대기 키만 지움. 실패 횟수는 그대로)
        redis.delete(redis.keys("jp:rl:login-wait:*"));
        b.login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
        // 성공하면 횟수 초기화 → 한 번 틀려도 바로 막히지 않음
        b.login(ADMIN_EMAIL, "wrong-password-x").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC1: 한 IP에서 1분에 20번까지 시도할 수 있고 21번째는 429다 (다른 IP는 영향 없음)")
    void limitsPerIp() throws Exception {
        Browser b = new Browser("198.51.100.3").withCsrf();
        for (int i = 0; i < 20; i++) {
            b.login("nobody" + i + "@test.local", "any-password-1234").andExpect(status().isUnauthorized());
        }
        b.login(ADMIN_EMAIL, ADMIN_PASSWORD)
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        new Browser("198.51.100.4").withCsrf().login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC1: 정지된 계정은 비밀번호가 맞을 때만 403 ACCOUNT_SUSPENDED, 틀리면 같은 401")
    void suspendedAccount() throws Exception {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE user_id = ?", other);
        Browser b = new Browser("198.51.100.5").withCsrf();
        b.login(OTHER_EMAIL, "wrong-password-0000").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value(FAIL));
        b.login(OTHER_EMAIL, OTHER_PASSWORD)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        b.get(ME).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC1: 로그인 성공·실패·막힘은 감사 기록(audit_logs)에 남고, 이메일·비밀번호는 남기지 않는다")
    void auditLogs() throws Exception {
        Browser b = new Browser("198.51.100.6").withCsrf();
        b.login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
        b.login(ADMIN_EMAIL, "wrong-password-0000").andExpect(status().isUnauthorized());
        b.login("nobody@test.local", "wrong-password-0000").andExpect(status().isUnauthorized());
        for (int i = 0; i < 4; i++) {
            b.login(ADMIN_EMAIL, "wrong-password-" + i);
        }
        b.login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isTooManyRequests());

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, actor, event, ip, user_agent, CAST(detail AS CHAR) AS detail FROM audit_logs ORDER BY audit_id");
        assertThat(rows).extracting(r -> r.get("event"))
            .containsSubsequence("LOGIN", "LOGIN_FAILED", "LOGIN_FAILED", "RATE_LIMITED");
        assertThat(rows.get(0)).containsEntry("event", "LOGIN").containsEntry("actor", "USER")
            .containsEntry("ip", "198.51.100.6");
        assertThat(((Number) rows.get(0).get("user_id")).longValue()).isEqualTo(ADMIN_ID);
        assertThat(((Number) rows.get(1).get("user_id")).longValue()).isEqualTo(ADMIN_ID);
        assertThat(rows.get(2).get("user_id")).isNull(); // 없는 계정
        for (Map<String, Object> row : rows) {
            String all = row.toString();
            assertThat(all).doesNotContain(ADMIN_EMAIL, "nobody@test.local", ADMIN_PASSWORD, "wrong-password");
        }
        assertThat(jdbc.queryForObject("SELECT last_login_at FROM users WHERE user_id = ?", LocalDateTime.class, ADMIN_ID))
            .isNotNull();
    }

    @Test
    @DisplayName("AC1: 이메일은 앞뒤 공백·대소문자를 무시하고, 빈 값은 400이다")
    void emailIsNormalized() throws Exception {
        Browser b = new Browser("198.51.100.7").withCsrf();
        b.login("  Admin@Test.LOCAL ", ADMIN_PASSWORD).andExpect(status().isOk());
        b.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------------ AC2 세션·쿠키·CSRF·로그아웃

    @Test
    @DisplayName("AC2: 로그인하면 내 정보(/me와 같은 모양)를 돌려주고, 세션을 새로 발급한다 (이전 세션 ID는 못 씀)")
    void loginIssuesNewSession() throws Exception {
        Browser b = new Browser("198.51.100.8").withCsrf();
        b.login(ADMIN_EMAIL, ADMIN_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
            .andExpect(jsonPath("$.name").value("관리자"))
            .andExpect(jsonPath("$.hasPassword").value(true))
            .andExpect(jsonPath("$.loginMethods[0]").value("EMAIL"));
        Cookie first = b.sid;
        assertThat(first).isNotNull();

        b.login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
        assertThat(b.sid.getValue()).isNotEqualTo(first.getValue());

        b.get(ME).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
        Browser old = new Browser("198.51.100.8");
        old.sid = first;
        old.get(ME).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("AC2: 세션 쿠키는 __Host-JP_SID · Secure · HttpOnly · SameSite=Lax · Path=/ (Domain 없음), 30일")
    void sessionCookieAttributes() throws Exception {
        Browser b = new Browser("198.51.100.9").withCsrf();
        MockHttpServletResponse response = b.login(ADMIN_EMAIL, ADMIN_PASSWORD).andReturn().getResponse();
        String setCookie = setCookie(response, SESSION_COOKIE);
        assertThat(setCookie).contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax", "Max-Age=2592000")
            .doesNotContain("Domain=");
    }

    @Test
    @DisplayName("AC2: CSRF — GET /auth/csrf가 __Host-XSRF-TOKEN 쿠키(JS가 읽을 수 있게 HttpOnly 아님)를 주고, 바꾸는 요청은 X-XSRF-TOKEN 헤더가 없거나 틀리면 403 CSRF_INVALID")
    void csrf() throws Exception {
        MockHttpServletResponse csrf = web.perform(get(CSRF)).andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(setCookie(csrf, XSRF_COOKIE)).contains("Path=/", "Secure").doesNotContain("HttpOnly", "Domain=");

        // 로그인도 CSRF 토큰이 있어야 한다
        Browser noToken = new Browser("198.51.100.10");
        noToken.login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        Browser b = new Browser("198.51.100.10").withCsrf();
        b.login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
        String memo = "{\"title\":\"메모\",\"content\":\"내용\"}";

        String token = b.xsrf;
        b.xsrf = null; // 헤더·쿠키 없이
        b.perform(post("/api/v1/memos").contentType(MediaType.APPLICATION_JSON).content(memo))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        b.xsrf = token;
        b.perform(post("/api/v1/memos").contentType(MediaType.APPLICATION_JSON).content(memo).header("X-XSRF-TOKEN", "wrong"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        b.perform(post("/api/v1/memos").contentType(MediaType.APPLICATION_JSON).content(memo)).andExpect(status().isCreated());
        b.get("/api/v1/memos").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("AC2: 로그아웃(이 기기)하면 그 기기만 로그아웃되고 다른 기기는 그대로다")
    void logoutThisDevice() throws Exception {
        Browser pc = loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.11");
        Browser phone = loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.12");
        Cookie pcSession = pc.sid;

        pc.perform(post(LOGOUT)).andExpect(status().isNoContent());
        pc.sid = pcSession; // 지운 쿠키를 다시 보내도
        pc.get(ME).andExpect(status().isUnauthorized());
        phone.get(ME).andExpect(status().isOk());
    }

    @Test
    @DisplayName("AC2: 모든 기기 로그아웃 — DELETE /me/sessions로 이 기기 빼고 모두, 이어서 로그아웃하면 전부")
    void logoutAllDevices() throws Exception {
        Browser pc = loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.13");
        Browser phone = loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.14");
        Browser tablet = loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.15");
        Browser otherUser = loggedIn(OTHER_EMAIL, OTHER_PASSWORD, "198.51.100.16");

        pc.perform(delete("/api/v1/me/sessions")).andExpect(status().isNoContent());
        phone.get(ME).andExpect(status().isUnauthorized());
        tablet.get(ME).andExpect(status().isUnauthorized());
        pc.get(ME).andExpect(status().isOk());
        otherUser.get(ME).andExpect(status().isOk()); // 다른 회원은 그대로

        pc.perform(post(LOGOUT)).andExpect(status().isNoContent());
        pc.get(ME).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AC2: 로그인한 회원은 자기 데이터만 본다 (두 계정으로 실제 로그인)")
    void twoAccountsSeeOwnData() throws Exception {
        Browser admin = loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.17");
        Browser user = loggedIn(OTHER_EMAIL, OTHER_PASSWORD, "198.51.100.18");
        long memo = idOf(admin.perform(post("/api/v1/memos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"admin 메모\",\"content\":\"x\"}")).andExpect(status().isCreated()));

        user.get("/api/v1/memos").andExpect(jsonPath("$.length()").value(0));
        user.get("/api/v1/memos/" + memo).andExpect(status().isNotFound());
        admin.get("/api/v1/memos/" + memo).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 13-1 로그인 유지

    @Test
    @DisplayName("13-1: 계정당 기기 10대 — 11번째 로그인하면 가장 오래 안 쓴 기기가 로그아웃된다")
    void tenDevicesPerAccount() throws Exception {
        List<Browser> devices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            devices.add(loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100." + (100 + i)));
        }
        for (int i = 1; i < 10; i++) {
            Thread.sleep(2);
            devices.get(i).get(ME).andExpect(status().isOk()); // 0번만 오래 안 씀
        }
        Browser eleventh = loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.120");

        devices.get(0).get(ME).andExpect(status().isUnauthorized());
        devices.get(1).get(ME).andExpect(status().isOk());
        eleventh.get(ME).andExpect(status().isOk());
    }

    @Test
    @DisplayName("13-1: 로그인부터 최대 30일 (쓰고 있어도 연장 없음)")
    void thirtyDaysMaximum() throws Exception {
        Browser b = loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.21");
        clock.advance(Duration.ofDays(30).minusMinutes(1));
        b.get(ME).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(2));
        b.get(ME).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("13-1: 14일 안 쓰면 만료 — 세션의 미사용 만료가 14일이다")
    void fourteenDaysIdle() throws Exception {
        loggedIn(ADMIN_EMAIL, ADMIN_PASSWORD, "198.51.100.22");
        Set<String> keys = redis.keys("jp:session:sessions:*");
        String sessionKey = keys.stream().filter(k -> !k.contains(":expires:")).findFirst().orElseThrow();
        assertThat(redis.opsForHash().get(sessionKey, "maxInactiveInterval"))
            .isEqualTo((int) Duration.ofDays(14).toSeconds());
    }

    @Test
    @DisplayName("13-1·정지: 로그인한 채로 정지되면 1분 안에 그 세션이 끊긴다 (정지를 풀어도 그 세션은 다시 로그인해야 함)")
    void suspendedWhileLoggedIn() throws Exception {
        Browser b = loggedIn(OTHER_EMAIL, OTHER_PASSWORD, "198.51.100.23");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE user_id = ?", other);
        b.get(ME).andExpect(status().isOk()); // 1분마다 확인
        clock.advance(Duration.ofSeconds(61));
        b.get(ME).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE user_id = ?", other);
        b.get(ME).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 준비

    private Browser loggedIn(String email, String password, String ip) throws Exception {
        Browser b = new Browser(ip).withCsrf();
        b.login(email, password).andExpect(status().isOk());
        return b;
    }

    private static String setCookie(MockHttpServletResponse response, String name) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
            .filter(h -> h.startsWith(name + "="))
            .findFirst()
            .orElseThrow(() -> new AssertionError(name + " Set-Cookie 없음: " + response.getHeaders(HttpHeaders.SET_COOKIE)));
    }

    /** 쿠키를 기억하는 브라우저 하나 (기기 하나) */
    private final class Browser {

        private final String ip;
        private Cookie sid;
        private String xsrf;

        Browser(String ip) {
            this.ip = ip;
        }

        Browser withCsrf() throws Exception {
            MockHttpServletResponse response = web.perform(get(CSRF).with(r -> {
                r.setRemoteAddr(ip);
                return r;
            })).andExpect(status().isNoContent()).andReturn().getResponse();
            this.xsrf = response.getCookie(XSRF_COOKIE).getValue();
            return this;
        }

        ResultActions login(String email, String password) throws Exception {
            return perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
        }

        ResultActions get(String url) throws Exception {
            return perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
        }

        ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
            List<Cookie> cookies = new ArrayList<>();
            if (sid != null) {
                cookies.add(sid);
            }
            if (xsrf != null) {
                cookies.add(new Cookie(XSRF_COOKIE, xsrf));
                request.header("X-XSRF-TOKEN", xsrf);
            }
            if (!cookies.isEmpty()) {
                request.cookie(cookies.toArray(Cookie[]::new));
            }
            request.header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh) Chrome/141.0");
            ResultActions result = web.perform(request.with(r -> {
                r.setRemoteAddr(ip);
                return r;
            }));
            Cookie newSid = result.andReturn().getResponse().getCookie(SESSION_COOKIE);
            if (newSid != null) {
                sid = newSid.getMaxAge() == 0 ? null : newSid;
            }
            return result;
        }
    }
}
