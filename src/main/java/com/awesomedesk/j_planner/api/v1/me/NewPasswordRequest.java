package com.awesomedesk.j_planner.api.v1.me;

/** 비밀번호 변경·추가 {@code PUT /me/password} (US-37) */
public record NewPasswordRequest(String newPassword) {
}
