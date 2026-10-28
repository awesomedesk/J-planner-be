package com.awesomedesk.j_planner.api.v1.auth.google;

import com.awesomedesk.j_planner.common.auth.DeviceSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.context.NullSecurityContextRepository;

/**
 * 구글 로그인 (OpenID Connect, US-36, 08 13-3·13-5). Spring Security OAuth2 Login을 쓴다:
 * <ul>
 *   <li>시작: state(CSRF)·nonce(재사용 방지)·PKCE S256(code_challenge) — 등록 정보의 requireProofKey + openid scope</li>
 *   <li>콜백 {@code /api/v1/auth/google/callback}: state 확인(시작한 세션에서만) → code + code_verifier + 클라이언트 비밀값으로 토큰 →
 *       ID 토큰 서명(구글 공개키)·iss·aud(우리 클라이언트 ID)·exp·nonce 확인</li>
 *   <li>redirect URI는 설정한 FE 주소 + 콜백 경로 하나 (요청 Host로 만들지 않는다 — 구글 콘솔에 등록한 값과 정확히 일치)</li>
 *   <li>사용자 정보 주소는 부르지 않는다 — sub·email·email_verified·name은 ID 토큰에 있다</li>
 *   <li>구글 토큰(access token)은 저장하지 않는다 ({@link #noAuthorizedClients()}) — 로그인 확인에만 쓰고 구글 API는 부르지 않는다</li>
 * </ul>
 * scope는 openid email profile만 (구글 앱 심사 불필요, 11 3-6).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleOidcConfig {

    @Bean
    GoogleFlow.Redirects googleRedirects(@Value("${app.web.base-url}") String webBaseUrl) {
        return new GoogleFlow.Redirects(webBaseUrl);
    }

    @Bean
    public ClientRegistrationRepository googleClientRegistrations(GoogleProperties properties, GoogleFlow.Redirects redirects) {
        if (!properties.configured()) {
            log.warn("구글 로그인 설정(JP_GOOGLE_CLIENT_ID·JP_GOOGLE_CLIENT_SECRET)이 없어 구글 로그인을 끕니다 — 구글 버튼은 /login?error=GOOGLE_FAILED");
            return registrationId -> null;
        }
        ClientRegistration google = ClientRegistration.withRegistrationId(GoogleFlow.REGISTRATION_ID)
            .clientName("Google")
            .clientId(properties.clientId())
            .clientSecret(properties.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirects.callbackUri())
            .scope("openid", "email", "profile")
            .authorizationUri(properties.authorizationUri())
            .tokenUri(properties.tokenUri())
            .jwkSetUri(properties.jwkSetUri())
            .issuerUri(properties.issuer())
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build())
            .build();
        return registrationId -> GoogleFlow.REGISTRATION_ID.equals(registrationId) ? google : null;
    }

    @Bean
    GoogleAuthorizationRequestStore googleAuthorizationRequestStore() {
        return new GoogleAuthorizationRequestStore();
    }

    @Bean
    GoogleAuthorizationRequests googleAuthorizationRequests(ClientRegistrationRepository googleClientRegistrations, GoogleProperties properties,
                                                            GoogleAccounts accounts) {
        return new GoogleAuthorizationRequests(googleClientRegistrations, properties, accounts);
    }

    @Bean
    GoogleLoginHandler googleLoginHandler(GoogleAccounts accounts, DeviceSessions deviceSessions, GoogleFlow.Redirects redirects,
                                          GoogleSignupService signups) {
        return new GoogleLoginHandler(accounts, deviceSessions, redirects, signups);
    }

    /** SecurityConfig의 oauth2Login 설정 */
    @Bean
    public Customizer<OAuth2LoginConfigurer<HttpSecurity>> googleLogin(ClientRegistrationRepository googleClientRegistrations,
                                                                       GoogleAuthorizationRequests requests,
                                                                       GoogleAuthorizationRequestStore store,
                                                                       GoogleLoginHandler handler) {
        return oauth2 -> oauth2
            .clientRegistrationRepository(googleClientRegistrations)
            .authorizedClientRepository(noAuthorizedClients())
            .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(requests).authorizationRequestRepository(store))
            .loginProcessingUrl(GoogleFlow.CALLBACK_PATH)
            // 구글 인증 정보를 세션에 저장하지 않는다 — 로그인은 처리기가 DeviceSessions로 직접 (연결 중인 회원의 로그인도 그대로).
            // securityContextRepository(...)는 configure 단계에서 전역 저장소로 다시 덮이므로(7.1) 필터를 만든 뒤에 바꾼다
            .withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                @Override
                public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                    filter.setSecurityContextRepository(new NullSecurityContextRepository());
                    return filter;
                }
            })
            .successHandler(handler)
            .failureHandler(handler)
            // 기본 로그인 화면(/login)을 만들지 않게. 로그인 안 한 API 요청은 SecurityConfig의 401 그대로
            .loginPage("/login");
    }

    /** 구글 토큰을 어디에도 저장하지 않는다 */
    private static OAuth2AuthorizedClientRepository noAuthorizedClients() {
        return new OAuth2AuthorizedClientRepository() {
            @Override
            public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, Authentication principal, HttpServletRequest request) {
                return null;
            }

            @Override
            public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal, HttpServletRequest request,
                                             HttpServletResponse response) {
            }

            @Override
            public void removeAuthorizedClient(String clientRegistrationId, Authentication principal, HttpServletRequest request,
                                               HttpServletResponse response) {
            }
        };
    }
}
