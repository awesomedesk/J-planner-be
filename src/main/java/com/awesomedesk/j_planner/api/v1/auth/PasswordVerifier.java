package com.awesomedesk.j_planner.api.v1.auth;

import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 비교. 해시가 없는 경우(없는 계정·임시 가입·비밀번호 없는 계정)에도 미리 만든 가짜 해시로 비교를 한 번 똑같이 해서
 * 응답 시간으로 가입 여부를 알 수 없게 한다 (US-34 AC1 '없는 계정도 같은 응답 시간').
 */
@Component
public class PasswordVerifier {

    private final PasswordEncoder encoder;
    private final String dummyHash;

    public PasswordVerifier(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    /** @param hash 저장된 해시. 없으면 null */
    public boolean matches(String rawPassword, String hash) {
        if (hash == null) {
            encoder.matches(rawPassword, dummyHash);
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }

    /** Argon2id m=19MiB(19456 KiB)·t=2·p=1, 솔트 16바이트, 해시 32바이트 (07 11-2, OWASP 권장 최소값) */
    public static PasswordEncoder argon2() {
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }
}
