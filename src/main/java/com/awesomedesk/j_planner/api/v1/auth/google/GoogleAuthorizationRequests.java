package com.awesomedesk.j_planner.api.v1.auth.google;

import com.awesomedesk.j_planner.common.auth.AuthUser;
import com.awesomedesk.j_planner.common.auth.AuthUserArgumentResolver;
import com.awesomedesk.j_planner.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * 구글로 보낼 인가 요청 만들기 (US-36). 두 주소에서만 만든다 — 다른 요청은 모두 null(그냥 지나감).
 * <ul>
 *   <li>{@code GET /api/v1/auth/google}: 로그인·가입. {@code popup=true}(다시 로그인 창) 또는 {@code next=/사이트 안 경로}</li>
 *   <li>{@code GET /api/v1/me/identities/google/connect}: 내 계정에 연결. 로그인 + 구글 없음 + 재인증 5분 안일 때만,
 *       아니면 null → 401(로그인 검사) 또는 MeController가 403·409. 구글에서 계정을 고르게 {@code prompt=select_account}</li>
 *   <li>{@code GET /api/v1/me/reauth/google} (US-37): 구글이 연결된 회원의 재인증. {@code prompt=login}·{@code max_age=0}으로 구글에서 다시 로그인</li>
 * </ul>
 * state·nonce·PKCE(S256)·scope·redirect URI는 Spring Security 기본 구현이 넣는다 (등록 정보 {@link GoogleOidcConfig}).
 * 구글 설정이 없으면(로컬) null → {@link GoogleStartController}가 실패로 돌려보낸다.
 */
class GoogleAuthorizationRequests implements OAuth2AuthorizationRequestResolver {

    /** 쓰지 않는 기본 주소 — 실제로는 아래 두 주소만 {@link #resolve(HttpServletRequest)}가 고른다 */
    private static final String UNUSED_BASE_URI = "/api/v1/auth/oauth2-unused";

    private final GoogleProperties properties;
    private final GoogleAccounts accounts;
    private final DefaultOAuth2AuthorizationRequestResolver login;
    private final DefaultOAuth2AuthorizationRequestResolver connect;
    private final DefaultOAuth2AuthorizationRequestResolver reauth;

    GoogleAuthorizationRequests(ClientRegistrationRepository clients, GoogleProperties properties, GoogleAccounts accounts) {
        this.properties = properties;
        this.accounts = accounts;
        this.login = new DefaultOAuth2AuthorizationRequestResolver(clients, UNUSED_BASE_URI);
        this.connect = new DefaultOAuth2AuthorizationRequestResolver(clients, UNUSED_BASE_URI);
        this.connect.setAuthorizationRequestCustomizer(builder -> builder.additionalParameters(p -> p.put("prompt", "select_account")));
        // 재인증: 구글에서 반드시 다시 로그인 (콜백에서 ID 토큰의 auth_time도 확인)
        this.reauth = new DefaultOAuth2AuthorizationRequestResolver(clients, UNUSED_BASE_URI);
        this.reauth.setAuthorizationRequestCustomizer(builder -> builder.additionalParameters(p -> {
            p.put("prompt", "login");
            p.put("max_age", "0");
        }));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) || !properties.configured()) {
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (GoogleFlow.START_PATH.equals(path)) {
            return startLogin(request);
        }
        if (GoogleFlow.CONNECT_PATH.equals(path)) {
            return startConnect(request);
        }
        if (GoogleFlow.REAUTH_PATH.equals(path)) {
            return startReauth(request);
        }
        return null;
    }

    /** 다른 곳(ClientAuthorizationRequiredException)에서 부르는 경로 — 쓰지 않는다 */
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return null;
    }

    private OAuth2AuthorizationRequest startLogin(HttpServletRequest request) {
        boolean popup = "true".equals(request.getParameter("popup"));
        String next = popup ? null : GoogleFlow.safeNext(request.getParameter("next"));
        OAuth2AuthorizationRequest base = login.resolve(request, GoogleFlow.REGISTRATION_ID);
        if (base == null) {
            return null;
        }
        return OAuth2AuthorizationRequest.from(base).attributes(a -> {
            a.put(GoogleFlow.PURPOSE, GoogleFlow.Purpose.LOGIN.name());
            a.put(GoogleFlow.POPUP, popup);
            if (next != null) {
                a.put(GoogleFlow.NEXT, next);
            }
        }).build();
    }

    /** 재인증: 로그인했고 구글이 연결된 회원만. 아니면 null → 401(로그인 검사) 또는 MeController가 404 */
    private OAuth2AuthorizationRequest startReauth(HttpServletRequest request) {
        long userId;
        try {
            userId = AuthUserArgumentResolver.current().userId();
            accounts.checkReauth(userId);
        } catch (ApiException notAllowed) {
            return null;
        }
        OAuth2AuthorizationRequest base = reauth.resolve(request, GoogleFlow.REGISTRATION_ID);
        if (base == null) {
            return null;
        }
        return OAuth2AuthorizationRequest.from(base).attributes(a -> {
            a.put(GoogleFlow.PURPOSE, GoogleFlow.Purpose.REAUTH.name());
            a.put(GoogleFlow.USER_ID, userId);
        }).build();
    }

    private OAuth2AuthorizationRequest startConnect(HttpServletRequest request) {
        long userId;
        try {
            AuthUser user = AuthUserArgumentResolver.current();
            accounts.checkConnect(user.userId(), request);
            userId = user.userId();
        } catch (ApiException notAllowed) {
            return null; // 401(로그인 검사) 또는 MeController가 403·409로 답한다
        }
        OAuth2AuthorizationRequest base = connect.resolve(request, GoogleFlow.REGISTRATION_ID);
        if (base == null) {
            return null;
        }
        return OAuth2AuthorizationRequest.from(base).attributes(a -> {
            a.put(GoogleFlow.PURPOSE, GoogleFlow.Purpose.CONNECT.name());
            a.put(GoogleFlow.USER_ID, userId);
        }).build();
    }
}
