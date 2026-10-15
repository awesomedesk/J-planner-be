package com.awesomedesk.j_planner.api.v1.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-33 AC1 유출 비밀번호 차단 — HIBP Pwned Passwords k-익명성 API (08 13-3, 12 문서).
 * 비밀번호 SHA-1의 앞 5글자만 보내고, 돌아온 '뒤 35글자:횟수' 목록에서 찾는다 (원문·전체 해시는 보내지 않음).
 * 실제 요청 대신 가짜 응답으로 — 네트워크 없이.
 */
class HibpBreachedPasswordsTest {

    private static final String PASSWORD = "password1234567890";

    @Test
    @DisplayName("SHA-1 앞 5글자(대문자)만 보낸다")
    void sendsOnlyPrefix() {
        List<String> asked = new ArrayList<>();
        HibpBreachedPasswords hibp = new HibpBreachedPasswords(prefix -> {
            asked.add(prefix);
            return "";
        });
        hibp.isBreached(PASSWORD);
        String sha1 = HibpBreachedPasswords.sha1Hex(PASSWORD);
        assertThat(sha1).hasSize(40).matches("[0-9A-F]+");
        assertThat(asked).containsExactly(sha1.substring(0, 5));
    }

    @Test
    @DisplayName("응답 목록에 뒤 35글자가 있고 횟수가 1 이상이면 유출")
    void found() {
        String suffix = HibpBreachedPasswords.sha1Hex(PASSWORD).substring(5);
        HibpBreachedPasswords hibp = new HibpBreachedPasswords(prefix ->
            "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + suffix + ":42\r\n");
        assertThat(hibp.isBreached(PASSWORD)).isTrue();
    }

    @Test
    @DisplayName("목록에 없거나, 덧붙인 가짜 줄(Add-Padding, 횟수 0)이면 유출 아님")
    void notFoundOrPadding() {
        String suffix = HibpBreachedPasswords.sha1Hex(PASSWORD).substring(5);
        assertThat(new HibpBreachedPasswords(prefix -> "0018A45C4D1DEF81644B54AB7F969B88D65:3").isBreached(PASSWORD)).isFalse();
        assertThat(new HibpBreachedPasswords(prefix -> suffix + ":0").isBreached(PASSWORD)).isFalse();
    }

    @Test
    @DisplayName("HIBP에 닿지 못하면(오류·시간 초과) 유출 아님으로 보고 가입을 막지 않는다 (경고 로그)")
    void failOpen() {
        HibpBreachedPasswords hibp = new HibpBreachedPasswords(prefix -> {
            throw new IllegalStateException("timeout");
        });
        assertThat(hibp.isBreached(PASSWORD)).isFalse();
    }
}
