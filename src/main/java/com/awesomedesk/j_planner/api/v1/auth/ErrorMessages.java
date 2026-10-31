package com.awesomedesk.j_planner.api.v1.auth;

/** 로그인 화면에 그대로 보이는 문구 (08 13-3) */
final class ErrorMessages {

    /** 로그인 실패는 이 문구 하나 — 없는 계정·틀린 비밀번호·비밀번호 없는 계정·계정별 대기 모두 */
    static final String LOGIN_FAILED = "이메일 또는 비밀번호가 맞지 않아요";
    static final String SUSPENDED = "이용이 제한된 계정이에요.";

    private ErrorMessages() {
    }
}
