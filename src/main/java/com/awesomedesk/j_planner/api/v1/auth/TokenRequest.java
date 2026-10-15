package com.awesomedesk.j_planner.api.v1.auth;

/** 메일 링크의 토큰을 보내는 요청 (가입 인증, 08 13-3) */
public record TokenRequest(String token) {
}
