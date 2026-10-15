package com.awesomedesk.j_planner.api.v1.auth;

/**
 * 이메일 가입 요청 (08 13-3). 검사는 SignupService가 한다 — 이메일은 앞뒤 공백·대문자를 정리한 뒤 형식을 보고,
 * 비밀번호는 PasswordPolicy(400 PASSWORD_REJECTED)로.
 */
public record SignupRequest(String email, String password, String name, Boolean ageOver14) {
}
