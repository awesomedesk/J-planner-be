package com.awesomedesk.j_planner.api.v1.auth.google;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/auth/google}은 보통 Spring Security 필터가 구글로 302를 보내 여기까지 오지 않는다.
 * 여기에 왔다 = 구글 설정이 없다(로컬에서 JP_GOOGLE_CLIENT_ID를 비워 둠) → FE 로그인 화면에 GOOGLE_FAILED.
 */
@RestController
@RequiredArgsConstructor
class GoogleStartController {

    private final GoogleFlow.Redirects redirects;

    @GetMapping(GoogleFlow.START_PATH)
    ResponseEntity<Void> notConfigured(@RequestParam(required = false) String popup, @RequestParam(required = false) String next) {
        boolean isPopup = "true".equals(popup);
        String location = redirects.loginFailure(isPopup, isPopup ? null : GoogleFlow.safeNext(next), GoogleFlow.GOOGLE_FAILED);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
