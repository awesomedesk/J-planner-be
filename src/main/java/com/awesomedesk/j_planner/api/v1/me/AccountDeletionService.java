package com.awesomedesk.j_planner.api.v1.me;

import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.auth.Reauthentication;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import com.awesomedesk.j_planner.common.mail.AuthMailer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴 {@code DELETE /me} (US-37 AC2, D-060: 즉시 삭제, 07 11-2: soft delete 없음·CASCADE 없음).
 * 재인증 5분(403) → 감사 기록 ACCOUNT_DELETED(회원 행을 지우기 전에, user_id만) → 모든 기기 로그아웃
 * → 작성 데이터·로그인 정보·회원 행 삭제(한 트랜잭션, FK 순서) → 탈퇴 완료 메일 → 204.
 * 남는 것: audit_logs(user_id 숫자만, 1년 보관). 백업에서 지워지는 주기는 처리방침(US-39·44).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    /** 지우는 순서 — 자식 → 부모 (calendar_details는 calendars를 거쳐서) */
    private static final String[] BY_USER = {
        "todos", "calendars", "ddays", "diaries", "memos", "sidebar_items", "user_settings", "categories",
        "password_credentials", "auth_tokens", "user_identities", "dormant_profiles"
    };

    private final JdbcTemplate jdbc;
    private final Reauthentication reauthentication;
    private final DeviceSessions deviceSessions;
    private final AuditLogger audit;
    private final AuthMailer mailer;
    private final TransactionTemplate transaction;

    public void delete(long userId, HttpServletRequest http) {
        reauthentication.require(http);
        Map<String, Object> member = jdbc.queryForMap("SELECT email, name FROM users WHERE user_id = ?", userId);
        audit.user(userId, AuditEvent.ACCOUNT_DELETED, http, Map.of());
        deviceSessions.endAll(userId, http);
        deleteRows(userId);
        String email = (String) member.get("email");
        if (email != null) {
            try {
                mailer.sendAccountDeleted(email, (String) member.get("name"));
            } catch (RuntimeException e) {
                // TODO(US-41) jp_auth_mail_total{type=NOTICE,result=FAILED}
                log.error("탈퇴 완료 메일 실패: {}", EmailMasker.mask(email), e);
            }
        }
    }

    /** 회원의 작성 데이터·로그인 정보·회원 행을 지운다 (한 트랜잭션, FK 순서). 탈퇴와 휴면 4년 뒤 삭제(US-38)가 같이 쓴다 — 새 회원별 테이블은 BY_USER에 */
    public void deleteRows(long userId) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM calendar_details WHERE calendar_id IN (SELECT calendar_id FROM calendars WHERE user_id = ?)", userId);
            for (String table : BY_USER) {
                jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
            }
            jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
        });
    }
}
