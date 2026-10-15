package com.awesomedesk.j_planner.api.v1.auth;

/** 비밀번호 재설정 요청 (08 13-3): 메일 링크의 토큰 + 새 비밀번호 */
public record PasswordResetRequest(String token, String newPassword) {
}
