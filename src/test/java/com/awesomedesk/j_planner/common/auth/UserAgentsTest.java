package com.awesomedesk.j_planner.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 로그인 기기 이름·IP 앞부분 (US-37, 08 13-4) */
class UserAgentsTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36 | Chrome · macOS",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/141.0 Safari/537.36 Edg/141.0 | Edge · Windows",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1 | Safari · iOS",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 CriOS/141.0 Mobile Safari/604.1 | Chrome · iOS",
        "Mozilla/5.0 (Linux; Android 15; SM-S928N) AppleWebKit/537.36 SamsungBrowser/28.0 Chrome/130.0 Mobile Safari/537.36 | Samsung Internet · Android",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0 | Firefox · Linux",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/141.0 Whale/4.30 Safari/537.36 | Whale · Windows",
        "curl/8.7.1 | 브라우저 · 기기"
    })
    @DisplayName("User-Agent → '브라우저 · OS'")
    void describe(String userAgent, String expected) {
        assertThat(UserAgents.describe(userAgent)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {"211.36.142.7 | 211.36.*.*", "2001:db8:85a3::8a2e:370:7334 | 2001:db8:*", "0:0:0:0:0:0:0:1 | 0:0:*"})
    @DisplayName("IP는 앞 두 자리만")
    void ipPrefix(String ip, String expected) {
        assertThat(UserAgents.ipPrefix(ip)).isEqualTo(expected);
    }
}
