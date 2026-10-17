package com.awesomedesk.j_planner.api.v1.auth.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 로그인 설정 {@code app.auth.google.*} (US-36). 클라이언트 ID·비밀값은 환경 변수(로컬은 .env)로만 받는다 — 저장소에 쓰지 않는다.
 * 주소 4개는 구글 기본값(application.yml)이고 테스트만 가짜 구글로 바꾼다.
 *
 * @param clientId         {@code JP_GOOGLE_CLIENT_ID} (Google Cloud Console의 OAuth 클라이언트 ID). 비면 구글 로그인이 꺼진다
 * @param clientSecret     {@code JP_GOOGLE_CLIENT_SECRET}
 * @param authorizationUri 구글 로그인 화면
 * @param tokenUri         code → 토큰
 * @param jwkSetUri        ID 토큰 서명 확인용 공개키
 * @param issuer           ID 토큰의 iss가 이 값이어야 한다
 */
@ConfigurationProperties("app.auth.google")
public record GoogleProperties(String clientId, String clientSecret, String authorizationUri, String tokenUri, String jwkSetUri, String issuer) {

    /** 클라이언트 ID·비밀값이 둘 다 있으면 켜진다. 로컬에서 비워 두면 구글 버튼은 /login?error=GOOGLE_FAILED로 돌아간다 */
    public boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
