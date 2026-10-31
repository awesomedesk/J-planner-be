package com.awesomedesk.j_planner.config;

import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.ProblemWriter;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

/**
 * 로그인 검사·CSRF (US-34, 08-api-design.md 13-1).
 * <ul>
 *   <li>로그인 없이: 상태 확인 {@code /api/v1/health}, 인증 {@code /api/v1/auth/**}. 그 밖의 {@code /api/**}는 로그인 필요 → 401 UNAUTHENTICATED
 *       (입력 검사보다 먼저, 필터에서 막는다)</li>
 *   <li>CSRF: Spring Security SPA 방식. 쿠키 {@code __Host-XSRF-TOKEN}(JS가 읽음) + 바꾸는 요청마다 헤더 {@code X-XSRF-TOKEN}.
 *       없거나 틀리면 403 CSRF_INVALID. 로그인·로그아웃도 포함</li>
 *   <li>로그인은 폼·HTTP Basic이 아니라 {@code POST /api/v1/auth/login}(JSON)에서 직접 한다 (LoginService)</li>
 *   <li>구글 로그인(US-36): OAuth2 Login 필터가 {@code GET /api/v1/auth/google}·{@code /api/v1/me/identities/google/connect} → 구글,
 *       {@code /api/v1/auth/google/callback} ← 구글. 설정은 {@code GoogleOidcConfig}</li>
 * </ul>
 * 세션(쿠키 __Host-JP_SID, Redis)은 {@link SessionConfig}.
 */
@Configuration
public class SecurityConfig {

    public static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";
    /** API 응답(JSON)은 아무것도 불러오거나 끼워 넣을 일이 없다. 화면의 CSP는 FE(Next)가 정한다 */
    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, ProblemWriter problems,
                                           SecurityContextRepository securityContextRepository,
                                           Customizer<OAuth2LoginConfigurer<HttpSecurity>> googleLogin) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health", "/api/v1/auth/**").permitAll()
                // 새 이메일 인증 링크(US-37)는 메일을 다른 기기에서 열 수 있어 로그인 없이
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/me/email/verify").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) -> problems.write(request, response, ErrorCode.UNAUTHENTICATED))
                .accessDeniedHandler((request, response, ex) -> problems.write(request, response,
                    ex instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.UNAUTHENTICATED)))
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            .oauth2Login(googleLogin)
            // 세션 고정 공격 방지는 우리 로그인(DeviceSessions.start: 이전 세션 버리고 새 ID)이 한다.
            // Spring Security의 ID 바꾸기를 같이 쓰면 Spring Session이 저장 전의 옛 ID를 다시 써 버려 로그인 전 세션 ID가 그대로 남는다 (US-36)
            .sessionManagement(session -> session.sessionFixation(fixation -> fixation.none()))
            .requestCache(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            // 보안 헤더 (US-41): 기본(nosniff, X-Frame-Options DENY, no-store) + Referrer-Policy, API 응답에 CSP.
            // HSTS는 HTTPS를 끝내는 앞단 프록시가 붙인다 (여기는 프록시 뒤 HTTP라 Spring이 붙이지 않음)
            .headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                    new StaticHeadersWriter("Content-Security-Policy", API_CSP))));
        return http.build();
    }

    /** 로그인 정보는 세션(Spring Session → Redis)에 둔다 */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE);
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax").maxAge(Duration.ofDays(30)));
        return repository;
    }

    /** 폼 로그인을 쓰지 않으므로 Spring Boot 기본 사용자(임시 비밀번호)를 만들지 않게 한다 */
    @Bean
    public UserDetailsService noUserDetails() {
        return username -> {
            throw new UsernameNotFoundException("폼 로그인은 쓰지 않습니다.");
        };
    }
}
