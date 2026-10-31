package com.awesomedesk.j_planner.api.v1.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API — 로그인 없이 호출 (US-33 가입, US-34 로그인, US-35 비밀번호 재설정). 명세: 08-api-design.md 13-3 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final SignupService signupService;
    private final PasswordResetService passwordResetService;

    /** 앱을 열 때 한 번: CSRF 쿠키(__Host-XSRF-TOKEN)를 받는다 */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // 토큰을 읽어야 쿠키가 만들어진다
        return ResponseEntity.noContent().build();
    }

    /** 이메일 가입 → 임시 가입 + 인증 메일 (US-33) */
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@RequestBody SignupRequest request, HttpServletRequest http) {
        signupService.signup(request, http);
        return ResponseEntity.accepted().build();
    }

    /** 인증 메일 다시 보내기. 항상 202 */
    @PostMapping("/signup/resend")
    public ResponseEntity<Void> resendSignupMail(@RequestBody EmailRequest request) {
        signupService.resend(request);
        return ResponseEntity.accepted().build();
    }

    /** 인증 링크 확인 → 회원 생성. 자동 로그인 안 함 (D-066) */
    @PostMapping("/signup/verify")
    public ResponseEntity<Void> verifySignup(@RequestBody TokenRequest request, HttpServletRequest http) {
        signupService.verify(request, http);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 비밀번호 찾기 → 회원이면 재설정 메일. 항상 202 (US-35) */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@RequestBody EmailRequest request, HttpServletRequest http) {
        passwordResetService.forgot(request, http);
        return ResponseEntity.accepted().build();
    }

    /** 비밀번호 재설정 → 204. 자동 로그인 안 함, 모든 기기 로그아웃 */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody PasswordResetRequest request, HttpServletRequest http) {
        passwordResetService.reset(request, http);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        return loginService.login(request, http, response);
    }

    /** 이 기기 로그아웃. 로그인 안 했어도 204 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        loginService.logout(http);
        return ResponseEntity.noContent().build();
    }
}
