package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.common.net.ClientIp;
import com.awesomedesk.j_planner.api.v1.me.MeService;
import com.awesomedesk.j_planner.api.v1.user.DormantAccounts;
import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 이메일 로그인 (US-34, 08 13-3·13-7).
 * <ol>
 *   <li>IP별 횟수 → 계정별 대기 확인 (넘으면 429)</li>
 *   <li>회원·비밀번호 해시 찾기 → 비교 (없는 계정도 가짜 해시로 한 번 비교 = 같은 시간). 휴면 회원은 dormant_profiles의 이메일로 찾는다 (US-38)</li>
 *   <li>틀리면 실패 수 +1, 401 문구 하나. 맞는데 정지면 403</li>
 *   <li>맞으면 실패 수 초기화, 휴면이면 복원(정지면 위에서 403이라 복원 안 함), 새 세션(기기 10대 유지), last_login_at·휴면 안내 기록 지움, 감사 기록</li>
 * </ol>
 * 실패도 감사 기록·카운터가 남아야 해서 트랜잭션으로 묶지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private static final Map<String, String> PASSWORD = Map.of("method", "PASSWORD");

    private final JdbcTemplate jdbc;
    private final ClientIp clientIp;
    private final PasswordCredentials passwordCredentials;
    private final PasswordVerifier passwordVerifier;
    private final LoginRateLimiter rateLimiter;
    private final DeviceSessions deviceSessions;
    private final AuditLogger audit;
    private final MeService meService;
    private final DormantAccounts dormantAccounts;
    private final Clock clock;

    public LoginResponse login(LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        Member member = findMember(email);
        Long userId = member == null ? null : member.id();
        try {
            rateLimiter.countIp(clientIp.of(http));
        } catch (TooManyRequestsException e) {
            audit.user(userId, AuditEvent.RATE_LIMITED, http, Map.of("rule", "login-ip"));
            throw e;
        }
        try {
            rateLimiter.checkAccount(email);
        } catch (TooManyRequestsException e) {
            audit.user(userId, AuditEvent.RATE_LIMITED, http, Map.of("rule", "login-account"));
            throw e;
        }

        String hash = member == null ? null : passwordCredentials.findHash(member.id());
        if (!passwordVerifier.matches(request.password(), hash)) {
            rateLimiter.recordFailure(email);
            audit.user(userId, AuditEvent.LOGIN_FAILED, http, PASSWORD);
            log.info("로그인 실패: {}", EmailMasker.mask(email));
            throw new ApiException(ErrorCode.UNAUTHENTICATED, ErrorMessages.LOGIN_FAILED);
        }
        if (member.suspended()) { // 정지 (D-069)
            audit.user(userId, AuditEvent.LOGIN_FAILED, http, Map.of("method", "PASSWORD", "reason", "SUSPENDED"));
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, ErrorMessages.SUSPENDED);
        }

        rateLimiter.recordSuccess(email);
        boolean restored = dormantAccounts.restore(member.id(), http);
        deviceSessions.start(member.id(), http, response);
        jdbc.update("UPDATE users SET last_login_at = ?, dormant_notice_sent_at = NULL WHERE user_id = ?", LocalDateTime.now(clock).withNano(0), member.id());
        audit.user(userId, AuditEvent.LOGIN, http, PASSWORD);
        return LoginResponse.of(meService.get(member.id()), restored);
    }

    public void logout(HttpServletRequest http) {
        Long userId = currentUserId();
        deviceSessions.end(http);
        if (userId != null) {
            audit.user(userId, AuditEvent.LOGOUT, http, Map.of());
        }
    }

    private Member findMember(String email) {
        List<Member> found = jdbc.query("""
                SELECT u.user_id, u.suspended_at IS NOT NULL AS suspended
                FROM users u LEFT JOIN dormant_profiles d ON d.user_id = u.user_id WHERE u.email = ? OR d.email = ?""",
            (rs, i) -> new Member(rs.getLong("user_id"), rs.getBoolean("suspended")), email, email);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static Long currentUserId() {
        try {
            return com.awesomedesk.j_planner.common.auth.AuthUserArgumentResolver.current().userId();
        } catch (ApiException notLoggedIn) {
            return null;
        }
    }

    private record Member(long id, boolean suspended) {
    }
}
