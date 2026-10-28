package com.awesomedesk.j_planner.api.v1.auth.google;

import com.awesomedesk.j_planner.api.v1.me.MeResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구글 가입 확인 화면 API (D-083, 06 SITE-05g) — 로그인 없이, 구글 콜백이 만든 대기 정보로. 명세 08 13-3 */
@RestController
@RequestMapping("/api/v1/auth/google/signup")
@RequiredArgsConstructor
class GoogleSignupController {

    private final GoogleSignupService signups;

    /** 화면에 채울 구글 이메일·이름·next. 대기가 없거나 10분 지났으면 400 GOOGLE_SIGNUP_EXPIRED */
    @GetMapping
    InfoResponse info(HttpServletRequest request) {
        GoogleSignupService.Info info = signups.info(request);
        return new InfoResponse(info.email(), info.name(), info.next());
    }

    /** [가입하기] → 201 내 정보 + 로그인 세션 */
    @PostMapping
    ResponseEntity<MeResponse> signup(@RequestBody SignupRequest body, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signups.complete(body.name(), body.ageOver14(), request, response));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record InfoResponse(String email, String name, String next) {
    }

    record SignupRequest(String name, Boolean ageOver14) {
    }
}
