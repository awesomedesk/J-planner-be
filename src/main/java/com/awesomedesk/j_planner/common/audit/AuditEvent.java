package com.awesomedesk.j_planner.common.audit;

/**
 * audit_logs.event 값 (08-api-design.md 13-8). 어드민 회원 현황(ADMIN)이 이 이름으로 센다 — 이름을 바꾸지 않는다.
 * 앞으로 더할 것: DORMANT·DORMANT_RESTORED(US-38)
 * 로그인·가입의 method: PASSWORD / GOOGLE (US-36), 가입은 EMAIL / GOOGLE
 */
public enum AuditEvent {
    LOGIN,
    LOGIN_FAILED,
    RATE_LIMITED,
    LOGOUT,
    LOGOUT_OTHERS,
    /** 가입 인증 끝 (회원 생성). detail {"method":"EMAIL"} (US-33), GOOGLE (US-36) */
    SIGNUP_COMPLETED,
    /** 비밀번호 바꿈. detail {"method":"RESET"} (US-35 재설정), CHANGE (US-37 변경) */
    PASSWORD_CHANGED,
    /** 내 계정에서 구글 연결. detail {"provider":"GOOGLE"} (US-36) */
    IDENTITY_LINKED,
    /** 구글 연결 해제. detail {"provider":"GOOGLE"} (US-36) */
    IDENTITY_UNLINKED,
    /** 새 이메일 인증 끝 → 이메일 바뀜 (US-37) */
    EMAIL_CHANGED,
    /** 탈퇴. users 행을 지우기 전에 남긴다 (US-37). user_id만 남고 개인정보 없음 */
    ACCOUNT_DELETED
}
