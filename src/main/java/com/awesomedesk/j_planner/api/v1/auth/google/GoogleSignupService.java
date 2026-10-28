package com.awesomedesk.j_planner.api.v1.auth.google;

import com.awesomedesk.j_planner.api.v1.me.MeResponse;
import com.awesomedesk.j_planner.api.v1.me.MeService;
import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 구글로 처음 가입할 때 확인 화면 한 장 (D-083, 06 SITE-05g, US-36).
 * <ol>
 *   <li>콜백: 처음 보는 구글이면 회원을 만들지 않고 이 세션(로그인 전이면 10분짜리)에 '구글 가입 대기'(sub·이메일·채워 둘 이름·next)를 둔다
 *       → FE {@code /signup/google}</li>
 *   <li>{@code GET /auth/google/signup}: 화면에 채울 구글 이메일·이름·next</li>
 *   <li>{@code POST /auth/google/signup {name, ageOver14}}: 이름 1~50자·만 14세 확인 → 새 회원 + 구글 연결 + 기본 데이터 + <b>로그인</b>
 *       (구글이 이미 본인 확인을 함 — 이메일 가입과 달리 자동 로그인)</li>
 * </ol>
 * 대기 정보는 10분. 없거나 지났으면 400 GOOGLE_SIGNUP_EXPIRED → FE가 '구글로 다시'.
 */
@Service
@RequiredArgsConstructor
public class GoogleSignupService {

    static final Duration PENDING_LIFETIME = Duration.ofMinutes(10);
    /** 세션 속성: 구글 가입 대기 */
    static final String PENDING = "jp.googleSignup";
    static final int NAME_MAX = GoogleAccounts.NAME_MAX;

    private final GoogleAccounts accounts;
    private final DeviceSessions deviceSessions;
    private final MeService meService;
    private final Clock clock;

    /** 세션에 두는 가입 대기 (Redis 세션에 JDK 직렬화) */
    record Pending(String sub, String email, String name, String next, long heldAtMillis) implements Serializable {
    }

    /** 확인 화면에 채울 값 */
    public record Info(String email, String name, String next) {
    }

    /** 콜백에서: 처음 보는 구글을 가입 대기로 둔다 (같은 세션의 이전 대기는 바뀐다) */
    void hold(GoogleProfile google, String next, HttpServletRequest request) {
        request.getSession(true).setAttribute(PENDING,
            new Pending(google.sub(), google.email(), GoogleAccounts.displayName(google), next, clock.millis()));
    }

    public Info info(HttpServletRequest request) {
        Pending pending = pending(request);
        return new Info(pending.email(), pending.name(), pending.next());
    }

    /**
     * [가입하기]: 대기 확인(400 GOOGLE_SIGNUP_EXPIRED) → 이름·만 14세(400 VALIDATION_FAILED) → 그 사이 같은 이메일 회원(409) →
     * 새 회원 + 로그인(새 세션 ID, 대기 정보도 함께 사라짐) → 내 정보.
     */
    public MeResponse complete(String rawName, Boolean ageOver14, HttpServletRequest request, HttpServletResponse response) {
        Pending pending = pending(request);
        String name = rawName == null ? "" : rawName.strip();
        List<FieldErrorDetail> errors = new ArrayList<>();
        int length = name.codePointCount(0, name.length());
        if (length < 1 || length > NAME_MAX) {
            errors.add(new FieldErrorDetail("name", "이름은 1~" + NAME_MAX + "자로 입력해 주세요."));
        }
        if (!Boolean.TRUE.equals(ageOver14)) {
            errors.add(new FieldErrorDetail("ageOver14", "만 14세 이상만 가입할 수 있어요."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultDetail(), errors);
        }

        GoogleProfile google = new GoogleProfile(pending.sub(), pending.email(), true, pending.name());
        Long userId;
        try {
            userId = accounts.completeSignup(google, name, request);
        } catch (ApiException conflict) { // 그 사이 같은 이메일 회원이 생김 → 이 대기는 더 쓸 수 없다
            clear(request);
            throw conflict;
        }
        if (userId == null) { // 같은 구글이 다른 창에서 먼저 가입함
            clear(request);
            throw new ApiException(ErrorCode.GOOGLE_SIGNUP_EXPIRED);
        }
        deviceSessions.start(userId, request, response);
        accounts.recordLogin(userId, request);
        return meService.get(userId);
    }

    private Pending pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING) instanceof Pending pending)) {
            throw new ApiException(ErrorCode.GOOGLE_SIGNUP_EXPIRED);
        }
        long now = clock.millis();
        if (now - pending.heldAtMillis() > PENDING_LIFETIME.toMillis() || now < pending.heldAtMillis()) {
            session.removeAttribute(PENDING);
            throw new ApiException(ErrorCode.GOOGLE_SIGNUP_EXPIRED);
        }
        return pending;
    }

    private static void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PENDING);
        }
    }
}
