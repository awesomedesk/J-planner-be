package com.awesomedesk.j_planner.api.v1.auth;

import com.awesomedesk.j_planner.api.v1.me.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API — 로그인 없이 호출 (US-34). 명세: 08-api-design.md 13-3 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;

    /** 앱을 열 때 한 번: CSRF 쿠키(__Host-XSRF-TOKEN)를 받는다 */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // 토큰을 읽어야 쿠키가 만들어진다
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return loginService.login(request, http, response);
    }

    /** 이 기기 로그아웃. 로그인 안 했어도 204 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        loginService.logout(http);
        return ResponseEntity.noContent().build();
    }
}
