package com.awesomedesk.j_planner.api.v1.auth;

/** 이메일만 보내는 요청 (가입 인증 메일 다시 보내기, 08 13-3) */
public record EmailRequest(String email) {
}
