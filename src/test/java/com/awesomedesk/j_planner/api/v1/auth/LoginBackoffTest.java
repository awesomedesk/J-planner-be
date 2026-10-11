package com.awesomedesk.j_planner.api.v1.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** US-34 AC1 연속 실패 시 점점 늦추기 (08 13-7): 5회부터 30초, 10회부터 5분, 20회부터 30분, 영구 잠금 없음 — DB 없이 */
class LoginBackoffTest {

    @ParameterizedTest(name = "실패 {0}번 → {1}초 기다림")
    @CsvSource({"0,0", "1,0", "4,0", "5,30", "9,30", "10,300", "19,300", "20,1800", "1000,1800"})
    void waitSeconds(int failures, long seconds) {
        assertThat(LoginBackoff.waitSeconds(failures)).isEqualTo(seconds);
    }

    @Test
    @DisplayName("아무리 많이 틀려도 30분이 최대 (영구 잠금 없음)")
    void neverLocksForever() {
        assertThat(LoginBackoff.waitSeconds(Integer.MAX_VALUE)).isEqualTo(1800);
    }
}
