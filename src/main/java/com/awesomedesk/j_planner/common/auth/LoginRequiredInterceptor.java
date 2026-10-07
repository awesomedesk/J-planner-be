package com.awesomedesk.j_planner.common.auth;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 데이터 API는 로그인한 회원만 (08-api-design.md 13-2). 로그인 안 했으면 401 UNAUTHENTICATED.
 * 요청 값 검사(400)보다 먼저 확인한다 — 로그인 안 한 사람에게 입력 규칙을 알려주지 않게.
 * 로그인 없이 쓰는 주소(상태 확인, 이후 /auth)는 {@code WebConfig}에서 뺀다.
 */
public class LoginRequiredInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true; // CORS 사전 요청(OPTIONS) 등
        }
        if (!(request.getUserPrincipal() instanceof AuthUser)) {
            throw unauthenticated();
        }
        return true;
    }

    static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.getDefaultDetail());
    }
}
