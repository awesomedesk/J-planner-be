package com.awesomedesk.j_planner.api.v1.auth.google;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 구글 흐름의 주소·결과 (08-api-design.md 13-3·13-5, US-36).
 * <ul>
 *   <li>로그인: {@code GET /api/v1/auth/google[?next=/경로 | ?popup=true]} → 구글 → 콜백 → FE {@code next 또는 /}, 실패 {@code /login?error=코드}</li>
 *   <li>팝업(작성 중 '다시 로그인' 창, AC4): 결과를 {@code /login/google-done[?error=코드]}로 — FE가 팝업을 닫고 원래 창이 GET /me로 계정을 확인</li>
 *   <li>처음 보는 구글(D-083): FE {@code /signup/google} → {@code GET·POST /api/v1/auth/google/signup}. 팝업이면 {@code /login/google-done?error=GOOGLE_NOT_REGISTERED}</li>
 *   <li>연결: {@code GET /api/v1/me/identities/google/connect} → 구글 → 콜백 → FE {@code /me?linked=google}, 실패 {@code /me?error=코드}</li>
 *   <li>재인증(US-37): {@code GET /api/v1/me/reauth/google} → 구글(prompt=login, max_age=0) → 콜백 → FE {@code /me?reauth=google}, 실패 {@code /me?error=코드}</li>
 * </ul>
 * 콜백 주소는 하나(redirect URI 정확히 일치): {@code <app.web.base-url>/api/v1/auth/google/callback} — FE와 API가 같은 주소라 FE 주소 + 경로 (13-1).
 */
public final class GoogleFlow {

    public static final String REGISTRATION_ID = "google";
    public static final String START_PATH = "/api/v1/auth/google";
    public static final String CONNECT_PATH = "/api/v1/me/identities/google/connect";
    public static final String CALLBACK_PATH = "/api/v1/auth/google/callback";
    public static final String REAUTH_PATH = "/api/v1/me/reauth/google";

    /** 구글 왕복 동안 기억하는 값 (인가 요청의 attributes — 로그인 전 세션에 저장됨) */
    static final String PURPOSE = "jp.purpose";
    static final String POPUP = "jp.popup";
    static final String NEXT = "jp.next";
    static final String USER_ID = "jp.userId";

    /** FE로 보내는 결과 코드 (13-5) */
    static final String GOOGLE_FAILED = "GOOGLE_FAILED";
    static final String GOOGLE_EMAIL_ACCOUNT_EXISTS = "GOOGLE_EMAIL_ACCOUNT_EXISTS";
    static final String GOOGLE_ALREADY_LINKED = "GOOGLE_ALREADY_LINKED";
    static final String ACCOUNT_SUSPENDED = "ACCOUNT_SUSPENDED";
    /** 다시 로그인 팝업에서 처음 보는 구글 — 팝업에서는 가입하지 않는다 (D-083, FE 질문 A안) */
    static final String GOOGLE_NOT_REGISTERED = "GOOGLE_NOT_REGISTERED";
    /** 재인증을 이 회원에 연결된 구글이 아닌 다른 구글로 함 (US-37) */
    static final String GOOGLE_ACCOUNT_MISMATCH = "GOOGLE_ACCOUNT_MISMATCH";

    private static final int NEXT_MAX = 512;

    enum Purpose {
        LOGIN,
        CONNECT,
        REAUTH
    }

    private GoogleFlow() {
    }

    /**
     * 로그인 뒤 갈 곳: <b>사이트 안 경로만</b> (열린 리다이렉트 방지). '/'로 시작, '//'·'\'·제어 문자·ASCII 밖 글자 없음, 512자까지.
     *
     * @return 쓸 수 있으면 그 값, 아니면 null (→ '/')
     */
    static String safeNext(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > NEXT_MAX || raw.charAt(0) != '/' || raw.startsWith("//")) {
            return null;
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c <= 0x20 || c >= 0x7f || c == '\\') {
                return null;
            }
        }
        return raw;
    }

    /** FE 주소에 붙일 결과 주소들 */
    record Redirects(String webBaseUrl) {

        Redirects {
            webBaseUrl = webBaseUrl.endsWith("/") ? webBaseUrl.substring(0, webBaseUrl.length() - 1) : webBaseUrl;
        }

        String callbackUri() {
            return webBaseUrl + CALLBACK_PATH;
        }

        String loginSuccess(boolean popup, String next) {
            if (popup) {
                return webBaseUrl + "/login/google-done";
            }
            return webBaseUrl + (next == null ? "/" : next);
        }

        String loginFailure(boolean popup, String next, String code) {
            if (popup) {
                return webBaseUrl + "/login/google-done?error=" + code;
            }
            return webBaseUrl + "/login?error=" + code + (next == null ? "" : "&next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
        }

        /** 처음 보는 구글 → 가입 확인 화면 (D-083, 06 SITE-05g) */
        String signupConfirm() {
            return webBaseUrl + "/signup/google";
        }

        String connectSuccess() {
            return webBaseUrl + "/me?linked=google";
        }

        String reauthSuccess() {
            return webBaseUrl + "/me?reauth=google";
        }

        String connectFailure(String code) {
            return webBaseUrl + "/me?error=" + code;
        }
    }
}
