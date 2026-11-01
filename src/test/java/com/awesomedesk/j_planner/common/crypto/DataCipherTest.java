package com.awesomedesk.j_planner.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 일기·메모 내용 암호화 AES-256-GCM (US-41 AC4, D-061, 07 11-8) */
class DataCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

    private final DataCipher cipher = new DataCipher(KEY);

    @Test
    @DisplayName("암호화 → 복호화하면 원문. 형식 = 키 버전(1바이트, 1) + IV(12바이트, 매번 무작위) + 암호문 + 태그(16바이트)")
    void roundTrip() {
        byte[] sealed = cipher.encrypt("오늘의 일기 ✍️");
        assertThat(sealed[0]).isEqualTo((byte) 1);
        assertThat(sealed).hasSize(1 + 12 + "오늘의 일기 ✍️".getBytes(StandardCharsets.UTF_8).length + 16);
        assertThat(cipher.decrypt(sealed)).isEqualTo("오늘의 일기 ✍️");
        assertThat(new String(sealed, StandardCharsets.UTF_8)).doesNotContain("일기");
    }

    @Test
    @DisplayName("같은 내용도 암호문은 매번 다르다 (IV 무작위)")
    void randomIv() {
        assertThat(cipher.encrypt("같은 내용")).isNotEqualTo(cipher.encrypt("같은 내용"));
    }

    @Test
    @DisplayName("한 바이트라도 바뀌었거나 다른 키면 복호화하지 않고 예외 (GCM 태그 확인)")
    void tamperAndWrongKey() {
        byte[] sealed = cipher.encrypt("내용");
        byte[] tampered = sealed.clone();
        tampered[tampered.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DataCipher(OTHER).decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("암호문만 읽는다 — 키 버전을 0·2로 바꾼 값, 잘린 값, 빈 값, 원문 바이트는 모두 예외 (원문은 다른 칸에 따로 있다, PO #317)")
    void rejectsUnknownFormats() {
        for (byte version : new byte[] {0, 2}) {
            byte[] changed = cipher.encrypt("내용");
            changed[0] = version;
            assertThatThrownBy(() -> cipher.decrypt(changed)).as("버전 " + version).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> cipher.decrypt(new byte[] {1, 2, 3})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(new byte[0])).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("표시 없는 원문".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalStateException.class);
        byte[] cut = java.util.Arrays.copyOf(cipher.encrypt("잘린 값"), 20);
        assertThatThrownBy(() -> cipher.decrypt(cut)).isInstanceOf(IllegalStateException.class);
    }
}
