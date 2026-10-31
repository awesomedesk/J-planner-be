package com.awesomedesk.j_planner.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 일기·메모 내용 앱 암호화 AES-256-GCM (US-41, D-061·D-066, 07 11-8).
 * 저장 형식: 키 버전(1바이트) + IV(12바이트, 매번 무작위) + 암호문 + GCM 태그(16바이트). 바뀐 암호문·다른 키는 복호화하지 않는다.
 * 키 = 설정 {@code app.crypto.data-key} (base64 32바이트; tst·prd는 {@code JP_DATA_KEY} 필수, 로컬은 application-local.yml의 개발용 키).
 * <b>키를 잃으면 일기·메모를 되돌릴 수 없다</b> — 운영 키는 비밀 저장소와 서버 밖에 따로 보관. 키 교체는 버전 2를 더해서(나중에).
 * 첫 바이트가 키 버전이 아니면 암호화 전에 저장된 원문으로 보고 그대로 읽는다 ({@link NotesEncryptor}가 켤 때 암호화한다).
 */
@Component
public class DataCipher {

    static final byte KEY_VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DataCipher(@Value("${app.crypto.data-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("app.crypto.data-key(JP_DATA_KEY)가 없습니다 — 일기·메모 암호화 키(base64 32바이트)를 설정하세요");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.crypto.data-key는 base64여야 합니다");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("app.crypto.data-key는 32바이트(AES-256)여야 합니다");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public byte[] encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(new byte[] {KEY_VERSION});
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(KEY_VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    public String decrypt(byte[] stored) {
        if (!isSealed(stored)) {
            return new String(stored, StandardCharsets.UTF_8); // 암호화 전 원문
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES));
            cipher.updateAAD(new byte[] {KEY_VERSION});
            return new String(cipher.doFinal(stored, 1 + IV_BYTES, stored.length - 1 - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("복호화 실패 — 데이터가 바뀌었거나 키가 다릅니다");
        }
    }

    /** 이 형식으로 암호화된 값인지 (키 버전 + IV + 태그 길이 이상) */
    public static boolean isSealed(byte[] stored) {
        return stored != null && stored.length >= 1 + IV_BYTES + TAG_BITS / 8 && stored[0] == KEY_VERSION;
    }
}
