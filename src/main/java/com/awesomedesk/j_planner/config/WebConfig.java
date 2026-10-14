package com.awesomedesk.j_planner.config;

import com.awesomedesk.j_planner.common.auth.AuthUserArgumentResolver;
import com.awesomedesk.j_planner.common.auth.SessionPolicyInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

/**
 * - API 응답은 캐시하지 않는다 (08-api-design.md 2-7절)
 * - 로그인 세션 규칙: 최대 30일, 1분마다 정지 확인 (08 13-1)
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    /** WebMvcTest(컨트롤러만 띄우는 테스트)에는 없으므로 있을 때만 */
    private final ObjectProvider<SessionPolicyInterceptor> sessionPolicy;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        WebContentInterceptor noStore = new WebContentInterceptor();
        noStore.addCacheMapping(CacheControl.noStore(), "/api/**");
        registry.addInterceptor(noStore).addPathPatterns("/api/**");
        // 로그인 검사(401)는 Spring Security(SecurityConfig). 여기서는 세션 규칙(최대 30일·정지 확인)만
        sessionPolicy.ifAvailable(policy -> registry.addInterceptor(policy).addPathPatterns("/api/**").excludePathPatterns("/api/v1/health", "/api/v1/auth/**"));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthUserArgumentResolver());
    }
}
