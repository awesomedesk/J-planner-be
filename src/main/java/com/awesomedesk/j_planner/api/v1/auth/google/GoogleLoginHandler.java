package com.awesomedesk.j_planner.api.v1.auth.google;

import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.session.FindByIndexNameSessionRepository;

/**
 * 구글 콜백의 끝 (08 13-5). Spring Security가 code → 토큰 → ID 토큰 검증(서명·iss·aud·exp·nonce, PKCE)을 끝낸 뒤 여기로 온다.
 * 구글 인증 정보는 로그인으로 쓰지 않는다 — 우리 로그인은 언제나 {@link DeviceSessions#start}(AuthUser, 새 세션 ID, 기기 10대).
 * <ul>
 *   <li>로그인: 회원을 찾거나 만들어 로그인 → FE {@code next 또는 /}(팝업이면 /login/google-done). 안 되면 /login?error=코드</li>
 *   <li>연결: 시작한 회원이 아직 이 세션에 로그인해 있을 때만 연결 → /me?linked=google, 안 되면 /me?error=코드</li>
 *   <li>재인증(US-37): 시작한 회원의 구글 + 방금 다시 로그인(auth_time) → 이 기기 재인증 5분 → /me?reauth=google</li>
 * </ul>
 */
@Slf4j
class GoogleLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private final GoogleAccounts accounts;
    private final DeviceSessions deviceSessions;
    private final GoogleFlow.Redirects redirects;

    GoogleLoginHandler(GoogleAccounts accounts, DeviceSessions deviceSessions, GoogleFlow.Redirects redirects) {
        this.accounts = accounts;
        this.deviceSessions = deviceSessions;
        this.redirects = redirects;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        SecurityContextHolder.clearContext(); // 구글 인증 정보(OAuth2AuthenticationToken)는 버린다
        OAuth2AuthorizationRequest started = GoogleAuthorizationRequestStore.removedIn(request);
        if (started == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            response.sendRedirect(failure(started, GoogleFlow.GOOGLE_FAILED));
            return;
        }
        GoogleProfile google = GoogleProfile.from(oidcUser);
        try {
            if (purpose(started) == GoogleFlow.Purpose.CONNECT) {
                response.sendRedirect(connect(started, google, request));
            } else if (purpose(started) == GoogleFlow.Purpose.REAUTH) {
                response.sendRedirect(reauth(started, google, oidcUser, request));
            } else {
                response.sendRedirect(login(started, google, request, response));
            }
        } catch (RuntimeException e) {
            log.error("구글 콜백 처리 실패", e);
            response.sendRedirect(failure(started, GoogleFlow.GOOGLE_FAILED));
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        String reason = exception instanceof OAuth2AuthenticationException oauth ? oauth.getError().getErrorCode() : exception.getClass().getSimpleName();
        log.info("구글 로그인 실패: {}", reason); // 토큰·이메일은 남기지 않는다
        response.sendRedirect(failure(GoogleAuthorizationRequestStore.removedIn(request), GoogleFlow.GOOGLE_FAILED));
    }

    private String login(OAuth2AuthorizationRequest started, GoogleProfile google, HttpServletRequest request, HttpServletResponse response) {
        GoogleAccounts.LoginResult result = accounts.login(google, request);
        if (result.userId() == null) {
            return failure(started, result.error());
        }
        deviceSessions.start(result.userId(), request, response);
        accounts.recordLogin(result.userId(), request);
        return redirects.loginSuccess(popup(started), started.getAttribute(GoogleFlow.NEXT));
    }

    private String connect(OAuth2AuthorizationRequest started, GoogleProfile google, HttpServletRequest request) {
        Long userId = started.getAttribute(GoogleFlow.USER_ID);
        if (userId == null || !userId.equals(sessionUserId(request))) { // 그 사이 로그아웃·다른 계정 로그인
            return redirects.connectFailure(GoogleFlow.GOOGLE_FAILED);
        }
        String error = accounts.connect(userId, google, request);
        return error == null ? redirects.connectSuccess() : redirects.connectFailure(error);
    }

    private String reauth(OAuth2AuthorizationRequest started, GoogleProfile google, OidcUser oidcUser, HttpServletRequest request) {
        Long userId = started.getAttribute(GoogleFlow.USER_ID);
        if (userId == null || !userId.equals(sessionUserId(request))) {
            return redirects.connectFailure(GoogleFlow.GOOGLE_FAILED);
        }
        String error = accounts.reauth(userId, google, oidcUser.getAuthenticatedAt(), request);
        return error == null ? redirects.reauthSuccess() : redirects.connectFailure(error);
    }

    private String failure(OAuth2AuthorizationRequest started, String code) {
        if (started != null && purpose(started) != GoogleFlow.Purpose.LOGIN) {
            return redirects.connectFailure(code);
        }
        return redirects.loginFailure(started != null && popup(started), started == null ? null : started.getAttribute(GoogleFlow.NEXT), code);
    }

    private static GoogleFlow.Purpose purpose(OAuth2AuthorizationRequest started) {
        Object purpose = started.getAttribute(GoogleFlow.PURPOSE);
        for (GoogleFlow.Purpose p : GoogleFlow.Purpose.values()) {
            if (p.name().equals(purpose)) {
                return p;
            }
        }
        return GoogleFlow.Purpose.LOGIN;
    }

    private static boolean popup(OAuth2AuthorizationRequest started) {
        return Boolean.TRUE.equals(started.getAttribute(GoogleFlow.POPUP));
    }

    /** 이 세션에 로그인한 회원 (DeviceSessions가 넣은 회원별 찾기 값) */
    private static Long sessionUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object name = session == null ? null : session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);
        try {
            return name == null ? null : Long.valueOf(name.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
