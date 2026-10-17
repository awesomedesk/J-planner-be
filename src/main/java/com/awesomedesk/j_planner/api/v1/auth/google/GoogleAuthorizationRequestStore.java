package com.awesomedesk.j_planner.api.v1.auth.google;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.session.FindByIndexNameSessionRepository;

/**
 * 구글 왕복 동안 인가 요청(state·nonce·PKCE 검증값·목적)을 세션에 둔다 — state는 시작한 브라우저의 세션에서만 맞는다.
 * <ul>
 *   <li>로그인 전이면 이 세션은 <b>10분</b>만 유지한다 (구글 시작만 반복해 14일짜리 빈 세션이 Redis에 쌓이지 않게).
 *       로그인하면 {@code DeviceSessions}가 새 세션(14일)을 만든다</li>
 *   <li>콜백에서 꺼낸 요청은 요청 속성에 남겨 성공·실패 처리기가 목적(로그인/연결)을 알게 한다</li>
 * </ul>
 */
class GoogleAuthorizationRequestStore implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final Duration PRE_LOGIN_SESSION = Duration.ofMinutes(10);
    private static final String REMOVED = GoogleAuthorizationRequestStore.class.getName() + ".REMOVED";

    private final HttpSessionOAuth2AuthorizationRequestRepository delegate = new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
        HttpSession session = request.getSession(false);
        if (authorizationRequest != null && session != null
            && session.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME) == null) {
            session.setMaxInactiveInterval((int) PRE_LOGIN_SESSION.toSeconds());
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest removed = delegate.removeAuthorizationRequest(request, response);
        if (removed != null) {
            request.setAttribute(REMOVED, removed);
        }
        return removed;
    }

    /** 이번 콜백에서 꺼낸 인가 요청. state가 틀려 못 꺼냈으면 null */
    static OAuth2AuthorizationRequest removedIn(HttpServletRequest request) {
        return request.getAttribute(REMOVED) instanceof OAuth2AuthorizationRequest r ? r : null;
    }
}
