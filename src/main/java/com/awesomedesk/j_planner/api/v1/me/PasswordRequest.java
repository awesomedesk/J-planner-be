package com.awesomedesk.j_planner.api.v1.me;

/** 재인증 요청 {@code POST /me/reauth} (08 13-4) */
public record PasswordRequest(String password) {
}
