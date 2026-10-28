package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.auth.Reauthentication;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.EmailAlreadyRegisteredException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import com.awesomedesk.j_planner.common.mail.AuthMailer;
import com.awesomedesk.j_planner.common.ratelimit.RateLimits;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이메일 변경 (US-37, 08 13-4, D-066 2번).
 * <ol>
 *   <li>요청 {@code PUT /me/email}: 재인증 5분(403) → 형식·지금과 같음(400 errors email) → 회원별 하루 5회(429 email-change-user)
 *       → 이미 회원(409 + loginMethods) → 새 주소로 인증 링크(24시간, 이전 링크 무효) + 기존 주소에 알림 → 202.
 *       인증 전까지 이메일은 그대로 (바꿀 주소는 auth_tokens.new_email에만)</li>
 *   <li>인증 {@code POST /me/email/verify} (로그인 없이 — 메일을 다른 기기에서 열 수 있음): IP별 분당 10회(429 email-verify-ip)
 *       → 토큰 확인 → 그 사이 같은 이메일 회원이 생겼으면 400 TOKEN_INVALID → 이메일 바꿈 + 같은 이메일 임시 가입 삭제(한 트랜잭션)
 *       → 모든 기기 로그아웃 + EMAIL_CHANGED → 204. 자동 로그인 안 함</li>
 * </ol>
 * 링크 주소는 설정 {@code app.web.base-url}만 쓴다 (요청 Host를 쓰지 않음).
 */
@Slf4j
@Service
public class EmailChangeService {

    static final String PURPOSE = "EMAIL_CHANGE";
    static final Duration LINK_LIFETIME = Duration.ofHours(24);
    static final String VERIFY_PATH = "/email/verify?token=";

    private final JdbcTemplate jdbc;
    private final Reauthentication reauthentication;
    private final LoginMethods loginMethods;
    private final RateLimits rateLimits;
    private final AuthMailer mailer;
    private final DeviceSessions deviceSessions;
    private final AuditLogger audit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String webBaseUrl;

    public EmailChangeService(JdbcTemplate jdbc, Reauthentication reauthentication, LoginMethods loginMethods, RateLimits rateLimits,
                              AuthMailer mailer, DeviceSessions deviceSessions, AuditLogger audit, TransactionTemplate transaction, Clock clock,
                              @Value("${app.web.base-url}") String webBaseUrl) {
        this.jdbc = jdbc;
        this.reauthentication = reauthentication;
        this.loginMethods = loginMethods;
        this.rateLimits = rateLimits;
        this.mailer = mailer;
        this.deviceSessions = deviceSessions;
        this.audit = audit;
        this.transaction = transaction;
        this.clock = clock;
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
    }

    public void request(long userId, String rawEmail, HttpServletRequest http) {
        reauthentication.require(http);
        String email = EmailAddress.normalize(rawEmail);
        if (!EmailAddress.isValid(email)) {
            throw ApiException.validation("email", "이메일 주소를 확인해 주세요.");
        }
        Map<String, Object> member = jdbc.queryForMap("SELECT email, name FROM users WHERE user_id = ?", userId);
        String current = (String) member.get("email");
        String name = (String) member.get("name");
        if (email.equals(current)) {
            throw ApiException.validation("email", "지금 쓰는 이메일과 같아요.");
        }
        try {
            rateLimits.hit("email-change-user", String.valueOf(userId), 5, Duration.ofDays(1));
        } catch (TooManyRequestsException e) {
            audit.user(userId, AuditEvent.RATE_LIMITED, http, Map.of("rule", "email-change-user"));
            throw e;
        }
        // TODO(US-38) 휴면 회원 이메일(dormant_profiles.email)도 '이미 가입됨'
        Long owner = jdbc.query("SELECT user_id FROM users WHERE email = ?", rs -> rs.next() ? rs.getLong(1) : null, email);
        if (owner != null) {
            throw new EmailAlreadyRegisteredException(loginMethods.forConflict(owner));
        }

        LocalDateTime now = now();
        String token = SignupTokens.newToken();
        transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE auth_tokens SET used_at = ? WHERE user_id = ? AND purpose = ? AND used_at IS NULL", now, userId, PURPOSE);
            jdbc.update("INSERT INTO auth_tokens (user_id, purpose, token_hash, new_email, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                userId, PURPOSE, SignupTokens.hash(token), email, now.plus(LINK_LIFETIME), now);
        });
        sendQuietly(() -> mailer.sendEmailChange(email, name, webBaseUrl + VERIFY_PATH + token), email);
        if (current != null) {
            sendQuietly(() -> mailer.sendEmailChangeRequested(current, name), current);
        }
    }

    public void verify(TokenRequest request, HttpServletRequest http) {
        // TODO(US-41) 프록시 뒤 진짜 IP (getRemoteAddr)
        try {
            rateLimits.hit("email-verify-ip", http.getRemoteAddr(), 10, Duration.ofMinutes(1));
        } catch (TooManyRequestsException e) {
            audit.user(null, AuditEvent.RATE_LIMITED, http, Map.of("rule", "email-verify-ip"));
            throw e;
        }
        String token = request.token();
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        byte[] tokenHash = SignupTokens.hash(token.strip());
        LocalDateTime now = now();
        Long userId;
        try {
            userId = transaction.execute(status -> change(tokenHash, now));
        } catch (DuplicateKeyException race) { // 같은 이메일로 동시에 가입·변경
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        if (userId == null) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        deviceSessions.endAll(userId, http);
        audit.user(userId, AuditEvent.EMAIL_CHANGED, http, Map.of());
    }

    /** 토큰을 쓰고 이메일을 바꾼다. 토큰이 없거나 만료·사용됨, 또는 그 사이 같은 이메일 회원이 생겼으면 null(토큰은 무효로) */
    private Long change(byte[] tokenHash, LocalDateTime now) {
        List<Map<String, Object>> found = jdbc.queryForList(
            "SELECT user_id, new_email FROM auth_tokens WHERE token_hash = ? AND purpose = ? AND used_at IS NULL AND expires_at > ? FOR UPDATE",
            tokenHash, PURPOSE, now);
        if (found.isEmpty()) {
            return null;
        }
        long userId = ((Number) found.getFirst().get("user_id")).longValue();
        String email = (String) found.getFirst().get("new_email");
        jdbc.update("UPDATE auth_tokens SET used_at = ? WHERE user_id = ? AND purpose = ? AND used_at IS NULL", now, userId, PURPOSE);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email) > 0) {
            return null;
        }
        jdbc.update("DELETE FROM pending_signups WHERE email = ?", email); // 이메일 하나에 계정 하나 (D-061)
        jdbc.update("UPDATE users SET email = ?, updated_at = ? WHERE user_id = ?", email, now, userId);
        return userId;
    }

    private static void sendQuietly(Runnable send, String email) {
        try {
            send.run();
        } catch (RuntimeException e) {
            // TODO(US-41) jp_auth_mail_total{type=EMAIL_CHANGE|NOTICE,result=FAILED}
            log.error("이메일 변경 메일 실패: {}", EmailMasker.mask(email), e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
