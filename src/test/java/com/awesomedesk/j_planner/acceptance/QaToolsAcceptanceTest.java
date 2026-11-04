package com.awesomedesk.j_planner.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.awesomedesk.j_planner.api.v1.user.DormantAccounts;
import com.awesomedesk.j_planner.api.v1.user.QaTools;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * 로컬 실행 검수 도구 (PO #7 휴면, #354 재인증 만료). local 프로필에서 따로 한 번 실행하는 명령 — API·디버그 주소를 만들지 않는다.
 * 대상은 {@code qa-…@example.com} 회원 한 명뿐이고, 결과는 개수만 돌려준다 (이메일·세션 ID·토큰 없음).
 */
@DisplayName("로컬 실행 검수 도구 (QaTools)")
class QaToolsAcceptanceTest extends BrowserAcceptanceTest {

    private static final String QA = "qa-be-01@example.com";
    private static final String PW = "member-password-123";

    @Autowired
    private DormantAccounts dormancy;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessions;

    private QaTools tools;
    private long qa;
    private long other;

    @BeforeEach
    void setUp() {
        tools = new QaTools(jdbc, dormancy, sessions, clock);
        qa = createUser(QA);
        setPassword(qa, PW);
        other = createUser("member@test.local");
        jdbc.update("UPDATE users SET last_login_at = ?, dormant_notice_sent_at = ? WHERE user_id = ?", now().minusYears(2), now().minusDays(40), other);
    }

    @Test
    @DisplayName("qa-…@example.com 형식이 아니거나 없는 회원이면 아무것도 바꾸지 않고 멈춘다")
    void refusesOtherMembers() {
        assertThatThrownBy(() -> tools.run("member@test.local", "make-dormant")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.run("admin@test.local", "expire-reauth")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.run("qa-nobody@example.com", "make-dormant")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.run(QA, "drop-everything")).isInstanceOf(IllegalArgumentException.class);
        assertThat(status(other)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("dormant-notice: 그 QA 회원에게만 휴면 안내 메일 — 다른 대상 회원에게는 안 보냄")
    void noticeOnlyQa() {
        String summary = tools.run(QA, "dormant-notice");
        assertThat(mailer.sentTo(QA, "DORMANT_NOTICE")).hasSize(1);
        assertThat(mailer.sent()).hasSize(1);
        assertThat(summary).contains("notice=1").doesNotContain(QA);
    }

    @Test
    @DisplayName("make-dormant: 그 QA 회원만 휴면 — 조건을 갖춘 다른 회원은 그대로. 다시 로그인하면 복원")
    void dormantOnlyQa() throws Exception {
        String summary = tools.run(QA, "make-dormant");
        assertThat(status(qa)).isEqualTo("DORMANT");
        assertThat(status(other)).isEqualTo("ACTIVE");
        assertThat(summary).contains("dormant=1").doesNotContain(QA);

        browser("203.0.113.50").withCsrf().login(QA, PW).andExpect(status().isOk()).andExpect(jsonPath("$.restored").value(true));
    }

    @Test
    @DisplayName("expire-reauth: 그 QA 회원의 하나뿐인 세션에서 재인증만 만료 — 로그인은 그대로, 민감 작업은 403 REAUTH_REQUIRED, 새 재인증 뒤 다시 됨")
    void expireReauthOnly() throws Exception {
        Browser b = loggedIn(QA, PW, "203.0.113.51");
        b.postJson("/api/v1/me/reauth", "{\"password\":\"" + PW + "\"}").andExpect(status().isNoContent());

        String summary = tools.run(QA, "expire-reauth");
        assertThat(summary).contains("sessions=1").contains("expired=1").doesNotContain(QA);

        b.get("/api/v1/me").andExpect(status().isOk());
        b.perform(put("/api/v1/me/email").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"qa-be-01-new@example.com\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        b.postJson("/api/v1/me/reauth", "{\"password\":\"" + PW + "\"}").andExpect(status().isNoContent());
        b.perform(put("/api/v1/me/email").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"qa-be-01-new@example.com\"}"))
            .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("expire-reauth: 세션이 하나가 아니면(0개·2개 이상) 아무것도 바꾸지 않고 멈춘다")
    void expireReauthNeedsExactlyOneSession() throws Exception {
        assertThatThrownBy(() -> tools.run(QA, "expire-reauth")).isInstanceOf(IllegalStateException.class);
        Browser a = loggedIn(QA, PW, "203.0.113.52");
        a.postJson("/api/v1/me/reauth", "{\"password\":\"" + PW + "\"}").andExpect(status().isNoContent());
        loggedIn(QA, PW, "203.0.113.53");
        assertThatThrownBy(() -> tools.run(QA, "expire-reauth")).isInstanceOf(IllegalStateException.class);
        a.perform(put("/api/v1/me/email").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"qa-be-01-new@example.com\"}"))
            .andExpect(status().isAccepted());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private String status(long userId) {
        return jdbc.queryForObject("SELECT status FROM users WHERE user_id = ?", String.class, userId);
    }
}
