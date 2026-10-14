package com.awesomedesk.j_planner.config;

import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.common.error.ProblemWriter;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
 * </ul>
 * 세션(쿠키 __Host-JP_SID, Redis)은 {@link SessionConfig}.
 */
@Configuration
public class SecurityConfig {

    public static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, ProblemWriter problems,
                                           SecurityContextRepository securityContextRepository) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health", "/api/v1/auth/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) -> problems.write(request, response, ErrorCode.UNAUTHENTICATED))
                .accessDeniedHandler((request, response, ex) -> problems.write(request, response,
                    ex instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.UNAUTHENTICATED)))
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            .requestCache(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable);
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
