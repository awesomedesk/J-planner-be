package com.awesomedesk.j_planner.api.v1.auth.google;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 구글 결과 주소 규칙 (US-36, 08 13-5) */
class GoogleFlowTest {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/?view=week", "/todos/12?tab=done#top", "/a%20b"})
    @DisplayName("next: 사이트 안 경로는 그대로 쓴다")
    void safeNextAllowsSiteRelative(String next) {
        assertThat(GoogleFlow.safeNext(next)).isEqualTo(next);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"//evil.example.com", "https://evil.example.com", "/\\evil.example.com", "javascript:alert(1)",
        "evil", "/a b", "/한글", "/\r\nSet-Cookie:x", "/\t"})
    @DisplayName("next: 바깥 주소·//·\\·공백·제어 문자·ASCII 밖 글자는 버린다 (열린 리다이렉트·헤더 주입 방지)")
    void safeNextRejectsOthers(String next) {
        assertThat(GoogleFlow.safeNext(next)).isNull();
    }

    @Test
    @DisplayName("next: 512자까지")
    void safeNextMaxLength() {
        assertThat(GoogleFlow.safeNext("/" + "a".repeat(511))).isNotNull();
        assertThat(GoogleFlow.safeNext("/" + "a".repeat(512))).isNull();
    }

    @Test
    @DisplayName("결과 주소: FE 주소 끝의 / 는 하나로, 실패 next는 인코딩, 팝업은 /login/google-done")
    void redirects() {
        GoogleFlow.Redirects r = new GoogleFlow.Redirects("https://planner.example.com/");
        assertThat(r.callbackUri()).isEqualTo("https://planner.example.com/api/v1/auth/google/callback");
        assertThat(r.loginSuccess(false, null)).isEqualTo("https://planner.example.com/");
        assertThat(r.loginSuccess(false, "/?view=day")).isEqualTo("https://planner.example.com/?view=day");
        assertThat(r.loginSuccess(true, null)).isEqualTo("https://planner.example.com/login/google-done");
        assertThat(r.loginFailure(false, "/?view=day", "GOOGLE_FAILED"))
            .isEqualTo("https://planner.example.com/login?error=GOOGLE_FAILED&next=%2F%3Fview%3Dday");
        assertThat(r.loginFailure(true, null, "ACCOUNT_SUSPENDED")).isEqualTo("https://planner.example.com/login/google-done?error=ACCOUNT_SUSPENDED");
        assertThat(r.connectSuccess()).isEqualTo("https://planner.example.com/me?linked=google");
        assertThat(r.connectFailure("GOOGLE_ALREADY_LINKED")).isEqualTo("https://planner.example.com/me?error=GOOGLE_ALREADY_LINKED");
    }

    @Test
    @DisplayName("구글 가입 이름: 앞뒤 공백 정리, 없으면 이메일 @ 앞부분, 50자(이모지·한글 1자)까지")
    void displayName() {
        assertThat(GoogleAccounts.displayName(new GoogleProfile("s", "a@gmail.com", true, "  홍길동 "))).isEqualTo("홍길동");
        assertThat(GoogleAccounts.displayName(new GoogleProfile("s", "first.last@gmail.com", true, null))).isEqualTo("first.last");
        assertThat(GoogleAccounts.displayName(new GoogleProfile("s", "first.last@gmail.com", true, "   "))).isEqualTo("first.last");
        assertThat(GoogleAccounts.displayName(new GoogleProfile("s", "a@gmail.com", true, "😀".repeat(60)))).isEqualTo("😀".repeat(50));
    }
}
