package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.api.v1.me.LoginMethod;
import com.awesomedesk.j_planner.common.audit.AuditEvent;
import com.awesomedesk.j_planner.common.audit.AuditLogger;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.EmailAlreadyRegisteredException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.FieldErrorDetail;
import com.awesomedesk.j_planner.common.error.TooManyRequestsException;
import com.awesomedesk.j_planner.common.log.EmailMasker;
import com.awesomedesk.j_planner.common.mail.AuthMailer;
import com.awesomedesk.j_planner.common.ratelimit.RateLimits;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 이메일 가입 (US-33, 08 13-3·13-7, D-061·D-066).
 * <ol>
 *   <li>가입: IP별 횟수 → 입력값(이메일·이름·만 14세·비밀번호 규칙) → 이메일별 횟수 → 이미 회원이면 409 + loginMethods
 *       → 임시 가입 저장(같은 이메일은 교체) → 인증 메일 → 202</li>
 *   <li>다시 보내기: 항상 202. 유효한 임시 가입이 있고 60초 지났고 오늘 10통 안이면 새 링크로 메일</li>
 *   <li>인증: IP별 횟수 → 토큰 해시로 찾아 회원 생성(자동 로그인 안 함) → 201. 없거나 만료·사용됨이면 400 TOKEN_INVALID</li>
 * </ol>
 */
@Slf4j
@Service
public class SignupService {

    static final Duration PENDING_LIFETIME = Duration.ofHours(24);
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final String VERIFY_PATH = "/signup/verify?token=";
    static final int NAME_MAX = 50;

    private final JdbcTemplate jdbc;
    private final PendingSignups pendingSignups;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder encoder;
    private final AuthMailer mailer;
    private final RateLimits rateLimits;
    private final AuditLogger audit;
    private final Clock clock;
    private final String webBaseUrl;

    public SignupService(JdbcTemplate jdbc, PendingSignups pendingSignups, PasswordPolicy passwordPolicy, PasswordEncoder encoder,
                         AuthMailer mailer, RateLimits rateLimits, AuditLogger audit, Clock clock,
                         @Value("${app.web.base-url}") String webBaseUrl) {
        this.jdbc = jdbc;
        this.pendingSignups = pendingSignups;
        this.passwordPolicy = passwordPolicy;
        this.encoder = encoder;
        this.mailer = mailer;
        this.rateLimits = rateLimits;
        this.audit = audit;
        this.clock = clock;
        this.webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
    }

    public void signup(SignupRequest request, HttpServletRequest http) {
        // TODO(US-41) 프록시 뒤 진짜 IP (getRemoteAddr)
        String ip = http.getRemoteAddr();
        limit(http, "signup-ip", () -> {
            rateLimits.hit("signup-ip-10m", ip, 5, Duration.ofMinutes(10));
            rateLimits.hit("signup-ip-day", ip, 30, Duration.ofDays(1));
        });

        String email = EmailAddress.normalize(request.email());
        String name = request.name() == null ? "" : request.name().strip();
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (!EmailAddress.isValid(email)) {
            errors.add(new FieldErrorDetail("email", "이메일 주소를 확인해 주세요."));
        }
        int nameLength = name.codePointCount(0, name.length());
        if (nameLength < 1 || nameLength > NAME_MAX) {
            errors.add(new FieldErrorDetail("name", "이름은 1~" + NAME_MAX + "자로 입력해 주세요."));
        }
        if (!Boolean.TRUE.equals(request.ageOver14())) {
            errors.add(new FieldErrorDetail("ageOver14", "만 14세 이상만 가입할 수 있어요."));
        }
        if (request.password() == null) {
            errors.add(new FieldErrorDetail("password", "비밀번호를 입력해 주세요."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultDetail(), errors);
        }
        passwordPolicy.check(request.password());

        limit(http, "signup-email", () -> rateLimits.hit("signup-email-day", email, 5, Duration.ofDays(1)));

        Long userId = jdbc.query("SELECT user_id FROM users WHERE email = ?", rs -> rs.next() ? rs.getLong(1) : null, email);
        // TODO(US-38) 휴면 회원 이메일(dormant_profiles.email)도 '이미 가입됨'
        if (userId != null) {
            throw new EmailAlreadyRegisteredException(loginMethods(userId));
        }

        LocalDateTime now = now();
        String token = SignupTokens.newToken();
        pendingSignups.save(email, name, encoder.encode(request.password()), SignupTokens.hash(token), now, now.plus(PENDING_LIFETIME));
        send(email, name, token);
    }

    public void resend(EmailRequest request) {
        String email = EmailAddress.normalize(request.email());
        if (!EmailAddress.isValid(email)) {
            throw ApiException.validation("email", "이메일 주소를 확인해 주세요.");
        }
        LocalDateTime now = now();
        PendingSignups.Pending pending = pendingSignups.findActiveByEmail(email, now);
        if (pending == null || pending.lastSentAt().plus(RESEND_INTERVAL).isAfter(now)) {
            return; // 화면은 늘 202 — 임시 가입 여부가 드러나지 않게
        }
        if (!rateLimits.tryHit("signup-resend-day", email, 10, Duration.ofDays(1))) {
            log.info("인증 메일 다시 보내기 하루 한도: {}", EmailMasker.mask(email));
            return;
        }
        String token = SignupTokens.newToken();
        pendingSignups.renewToken(pending.id(), SignupTokens.hash(token), now);
        send(email, pending.name(), token);
    }

    public void verify(TokenRequest request, HttpServletRequest http) {
        // TODO(US-41) 프록시 뒤 진짜 IP (getRemoteAddr)
        String ip = http.getRemoteAddr();
        limit(http, "verify-ip", () -> rateLimits.hit("signup-verify-ip", ip, 10, Duration.ofMinutes(1)));
        String token = request.token();
        if (token == null || token.isBlank() || token.length() > 100) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = pendingSignups.verify(SignupTokens.hash(token.strip()), now());
        if (userId == null) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        audit.user(userId, AuditEvent.SIGNUP_COMPLETED, http, Map.of("method", "EMAIL"));
    }

    /** 이메일 계정 = EMAIL (비밀번호가 아직 없는 admin도 [로그인]/[비밀번호 찾기]로 안내). 구글 연결은 US-36에서 GOOGLE 추가 */
    private List<String> loginMethods(long userId) {
        List<String> methods = new ArrayList<>();
        // TODO(US-36) user_identities에 구글이 있으면 GOOGLE. 비밀번호 없이 구글만 있으면 GOOGLE만
        methods.add(LoginMethod.EMAIL.name());
        return methods;
    }

    private void send(String email, String name, String token) {
        try {
            mailer.sendSignupVerification(email, name, webBaseUrl + VERIFY_PATH + token);
        } catch (RuntimeException e) {
            // 메일이 안 가도 202 — 사용자는 '다시 보내기'를 누를 수 있다. TODO(US-41) jp_auth_mail_total{type=SIGNUP,result=FAILED}
            log.error("가입 인증 메일 실패: {}", EmailMasker.mask(email), e);
        }
    }

    private void limit(HttpServletRequest http, String auditRule, Runnable check) {
        try {
            check.run();
        } catch (TooManyRequestsException e) {
            audit.user(null, AuditEvent.RATE_LIMITED, http, Map.of("rule", auditRule));
            throw e;
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
