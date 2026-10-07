package com.awesomedesk.j_planner.config;

import com.awesomedesk.j_planner.common.auth.AuthUserArgumentResolver;
import com.awesomedesk.j_planner.common.auth.LoginRequiredInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.WebContentInterceptor;

/**
 * - API 응답은 캐시하지 않는다 (08-api-design.md 2-7절)
 * - 데이터 API는 로그인한 회원만 (08 13-2). 상태 확인은 로그인 없이
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 로그인 없이 쓰는 API */
    private static final String[] PUBLIC_API = {"/api/v1/health"};

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        WebContentInterceptor noStore = new WebContentInterceptor();
        noStore.addCacheMapping(CacheControl.noStore(), "/api/**");
        registry.addInterceptor(noStore).addPathPatterns("/api/**");
        registry.addInterceptor(new LoginRequiredInterceptor()).addPathPatterns("/api/**").excludePathPatterns(PUBLIC_API);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AuthUserArgumentResolver());
    }
}
