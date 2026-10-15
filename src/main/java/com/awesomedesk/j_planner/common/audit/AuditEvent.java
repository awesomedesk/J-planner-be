package com.awesomedesk.j_planner.common.audit;

/**
 * audit_logs.event 값 (08-api-design.md 13-8). 어드민 회원 현황(ADMIN)이 이 이름으로 센다 — 이름을 바꾸지 않는다.
 * 앞으로 더할 것: PASSWORD_CHANGED·EMAIL_CHANGED·ACCOUNT_DELETED(US-35·37), DORMANT·DORMANT_RESTORED(US-38)
 */
public enum AuditEvent {
    LOGIN,
    LOGIN_FAILED,
    RATE_LIMITED,
    LOGOUT,
    LOGOUT_OTHERS,
    /** 가입 인증 끝 (회원 생성). detail {"method":"EMAIL"} (US-33), GOOGLE (US-36) */
    SIGNUP_COMPLETED
}
