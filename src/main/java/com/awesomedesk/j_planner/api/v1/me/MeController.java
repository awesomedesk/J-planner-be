package com.awesomedesk.j_planner.api.v1.me;

import com.awesomedesk.j_planner.api.v1.auth.ReauthService;
import com.awesomedesk.j_planner.api.v1.auth.google.GoogleAccounts;
import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.AuthUser;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 계정 API (로그인 필요). 명세: 08-api-design.md 13-4.
 * US-34: 내 정보, 다른 기기 모두 로그아웃. US-36: 재인증(비밀번호), 연결된 구글 목록·연결 시작·해제
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeService meService;
    private final DeviceSessions deviceSessions;
    private final AuditLogger audit;
    private final ReauthService reauthService;
    private final GoogleAccounts googleAccounts;
    private final String webBaseUrl;

    public MeController(MeService meService, DeviceSessions deviceSessions, AuditLogger audit, ReauthService reauthService,
                        GoogleAccounts googleAccounts, @Value("${app.web.base-url}") String webBaseUrl) {
        this.meService = meService;
        this.deviceSessions = deviceSessions;
        this.audit = audit;
        this.reauthService = reauthService;
        this.googleAccounts = googleAccounts;
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
    }

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

    /** 비밀번호로 재인증 → 이 기기에서 5분 동안 민감 작업 가능 (US-36 구글 연결·해제, US-37) */
    @PostMapping("/reauth")
    public ResponseEntity<Void> reauth(AuthUser user, @RequestBody PasswordRequest request, HttpServletRequest http) {
        reauthService.reauth(user.userId(), request.password(), http);
        return ResponseEntity.noContent().build();
    }

    /** 연결된 로그인 수단 (구글 0~1개) */
    @GetMapping("/identities")
    public List<IdentityResponse> identities(AuthUser user) {
        return googleAccounts.list(user.userId()).stream().map(IdentityResponse::from).toList();
    }

    /**
     * 구글 연결 시작. 보통은 Spring Security 필터가 구글로 302를 보내 여기까지 오지 않는다 (GoogleAuthorizationRequests).
     * 여기에 왔다 = 시작할 수 없음 → 409 IDENTITY_ALREADY_CONNECTED / 403 REAUTH_REQUIRED, 둘 다 아니면 구글 설정이 없음 → /me?error=GOOGLE_FAILED
     */
    @GetMapping("/identities/google/connect")
    public ResponseEntity<Void> connectGoogle(AuthUser user, HttpServletRequest http) {
        googleAccounts.checkConnect(user.userId(), http);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(webBaseUrl + "/me?error=GOOGLE_FAILED")).build();
    }

    /** 구글 연결 해제 → 204 + 알림 메일. 없으면 404, 마지막 로그인 수단이면 409 LAST_IDENTITY, 재인증 5분 지나면 403 */
    @DeleteMapping("/identities/google")
    public ResponseEntity<Void> disconnectGoogle(AuthUser user, HttpServletRequest http) {
        googleAccounts.disconnect(user.userId(), http);
        return ResponseEntity.noContent().build();
    }
}
