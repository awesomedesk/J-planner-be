package com.awesomedesk.j_planner.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트용 가짜 구글 (OpenID Connect, US-36). 127.0.0.1의 빈 포트에서 토큰·공개키(JWKS) 주소만 연다.
 * <ul>
 *   <li>구글 로그인 화면은 없다 — 테스트가 우리 서버의 302 주소(구글 로그인 화면 주소)를 받아 {@link #approve}로 '로그인·동의'를 흉내 낸다</li>
 *   <li>토큰 주소는 진짜 구글처럼 확인한다: 1회용 code, redirect_uri 정확히 일치, PKCE(code_verifier → S256 = code_challenge), 클라이언트 비밀값.
 *       틀리면 400 invalid_grant</li>
 *   <li>ID 토큰은 RS256 서명, iss = {@link #issuer()}, aud = {@link #CLIENT_ID}, nonce = 시작할 때 보낸 값. {@link Tamper}로 망가뜨릴 수 있다</li>
 * </ul>
 */
public class FakeGoogle implements AutoCloseable {

    public static final String CLIENT_ID = "test-client.apps.googleusercontent.com";
    public static final String CLIENT_SECRET = "test-client-secret";

    /** ID 토큰을 일부러 망가뜨리는 방법 */
    public enum Tamper {
        NONE,
        /** 다른 키로 서명 (위조) */
        BAD_SIGNATURE,
        /** 다른 앱(aud)에 발급된 토큰 */
        WRONG_AUDIENCE,
        /** 다른 발급자(iss) */
        WRONG_ISSUER,
        /** 이미 만료 */
        EXPIRED,
        /** 시작할 때 보낸 nonce와 다름 (토큰 재사용) */
        WRONG_NONCE
    }

    /** 구글 계정 하나. email·name은 null이어도 된다 */
    public record GoogleUser(String sub, String email, boolean emailVerified, String name) {
    }

    /** 토큰 주소가 받은 요청 (확인용) */
    public record TokenRequest(Map<String, String> form, String authorization) {
    }

    private record Grant(Map<String, String> authorize, GoogleUser user, Tamper tamper) {
    }

    private final HttpServer server;
    private final RSAKey key;
    private final RSAKey otherKey;
    private final Map<String, Grant> codes = new ConcurrentHashMap<>();
    private final List<TokenRequest> tokenRequests = new CopyOnWriteArrayList<>();

    public FakeGoogle() {
        try {
            key = new RSAKeyGenerator(2048).keyID("fake-google-1").generate();
            otherKey = new RSAKeyGenerator(2048).keyID("fake-google-1").generate();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/token", this::token);
        server.createContext("/jwks", this::jwks);
        server.start();
    }

    public String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 구글 로그인 화면 주소 (실제로 열리지 않음 — 우리 서버가 302로 보내는 곳) */
    public String authorizationUri() {
        return issuer() + "/o/oauth2/v2/auth";
    }

    public String tokenUri() {
        return issuer() + "/token";
    }

    public String jwkSetUri() {
        return issuer() + "/jwks";
    }

    /**
     * 사용자가 구글에서 로그인·동의한 것처럼 1회용 code를 만든다.
     *
     * @param authorizeUrl 우리 서버가 302로 보낸 구글 로그인 화면 주소 (state·nonce·code_challenge·redirect_uri 포함)
     */
    public String approve(String authorizeUrl, GoogleUser user, Tamper tamper) {
        String code = "code-" + UUID.randomUUID();
        codes.put(code, new Grant(query(authorizeUrl), user, tamper));
        return code;
    }

    public String approve(String authorizeUrl, GoogleUser user) {
        return approve(authorizeUrl, user, Tamper.NONE);
    }

    public List<TokenRequest> tokenRequests() {
        return List.copyOf(tokenRequests);
    }

    public void clear() {
        codes.clear();
        tokenRequests.clear();
    }

    /** 주소의 쿼리를 풀어 이름 → 값 (같은 이름이 여러 번이면 마지막 값) */
    public static Map<String, String> query(String url) {
        String raw = URI.create(url).getRawQuery();
        return form(raw == null ? "" : raw);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ------------------------------------------------------------------ 토큰 주소

    private void token(HttpExchange exchange) throws IOException {
        Map<String, String> form = form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        tokenRequests.add(new TokenRequest(form, authorization));

        Grant grant = form.get("code") == null ? null : codes.remove(form.get("code"));
        if (!"POST".equals(exchange.getRequestMethod())
            || !"authorization_code".equals(form.get("grant_type"))
            || grant == null
            || !grant.authorize().get("redirect_uri").equals(form.get("redirect_uri"))
            || !clientAuthenticated(authorization, form)
            || !pkceMatches(form.get("code_verifier"), grant.authorize().get("code_challenge"))) {
            send(exchange, 400, Map.of("error", "invalid_grant"));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", "access-" + UUID.randomUUID());
        body.put("token_type", "Bearer");
        body.put("expires_in", 3599);
        body.put("scope", "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile");
        body.put("id_token", idToken(grant));
        send(exchange, 200, body);
    }

    private void jwks(HttpExchange exchange) throws IOException {
        send(exchange, 200, Map.of("keys", List.of(key.toPublicJWK().toJSONObject())));
    }

    private String idToken(Grant grant) {
        Instant now = Instant.now();
        GoogleUser user = grant.user();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
            .issuer(grant.tamper() == Tamper.WRONG_ISSUER ? "https://evil.example.com" : issuer())
            .subject(user.sub())
            .audience(grant.tamper() == Tamper.WRONG_AUDIENCE ? "other-app.apps.googleusercontent.com" : CLIENT_ID)
            .claim("azp", CLIENT_ID)
            .issueTime(Date.from(grant.tamper() == Tamper.EXPIRED ? now.minusSeconds(7200) : now))
            .expirationTime(Date.from(grant.tamper() == Tamper.EXPIRED ? now.minusSeconds(3600) : now.plusSeconds(3600)))
            .claim("nonce", grant.tamper() == Tamper.WRONG_NONCE ? "another-nonce" : grant.authorize().get("nonce"))
            .claim("email_verified", user.emailVerified());
        if (user.email() != null) {
            claims.claim("email", user.email());
        }
        if (user.name() != null) {
            claims.claim("name", user.name());
        }
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(grant.tamper() == Tamper.BAD_SIGNATURE ? otherKey : key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** client_secret_basic (Spring 기본): Basic base64(urlencode(id):urlencode(secret)) */
    private static boolean clientAuthenticated(String authorization, Map<String, String> form) {
        if (authorization != null && authorization.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return colon > 0
                && CLIENT_ID.equals(URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8))
                && CLIENT_SECRET.equals(URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8));
        }
        return CLIENT_ID.equals(form.get("client_id")) && CLIENT_SECRET.equals(form.get("client_secret"));
    }

    /** PKCE S256: base64url(SHA-256(code_verifier)) == code_challenge */
    public static boolean pkceMatches(String verifier, String challenge) {
        if (verifier == null || challenge == null) {
            return false;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).equals(challenge);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> form(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            values.put(name, value);
        }
        return values;
    }

    private static void send(HttpExchange exchange, int status, Map<String, ?> body) throws IOException {
        byte[] bytes = JSONObjectUtils.toJSONString(new LinkedHashMap<>(body)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
