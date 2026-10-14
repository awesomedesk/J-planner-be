package com.awesomedesk.j_planner.api.v1.me;

import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.AuthUser;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 계정 API (로그인 필요). 명세: 08-api-design.md 13-4. US-34: 내 정보, 다른 기기 모두 로그아웃 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;
    private final DeviceSessions deviceSessions;
    private final AuditLogger audit;

    @GetMapping
    public MeResponse get(AuthUser user) {
        return meService.get(user.userId());
    }

    /** 이 기기 빼고 모두 로그아웃 (US-34 AC2 '모든 기기'. 이어서 POST /auth/logout 하면 전부) */
    @DeleteMapping("/sessions")
    public ResponseEntity<Void> logoutOthers(AuthUser user, HttpServletRequest request) {
        String current = request.getSession(false) == null ? "" : request.getSession(false).getId();
        int ended = deviceSessions.endOthers(user.userId(), current);
        audit.user(user.userId(), AuditEvent.LOGOUT_OTHERS, request, Map.of("count", String.valueOf(ended)));
        return ResponseEntity.noContent().build();
    }
}
