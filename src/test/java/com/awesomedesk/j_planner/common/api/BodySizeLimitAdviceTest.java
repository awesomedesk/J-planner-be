package com.awesomedesk.j_planner.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.awesomedesk.j_planner.common.error.ErrorCode;
import com.awesomedesk.j_planner.support.ApiExceptionAssertions;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;

/**
 * 길이를 모르는 본문(chunked, Content-Length 없음)도 1MiB에서 막는다 (US-41, PO #317 2번).
 * Content-Length가 있으면 RequestLimits.bodySize가 본문 전에 413, 없으면 JSON 본문을 읽는 순간 이 advice가 센다.
 */
class BodySizeLimitAdviceTest {

    private final BodySizeLimitAdvice advice = new BodySizeLimitAdvice();

    @Test
    @DisplayName("길이 모르는 본문 1MiB까지는 그대로 읽힌다")
    void upToLimit() throws Exception {
        assertThat(readAll(advice.beforeBodyRead(chunked(RequestLimits.MAX_BODY_BYTES), null, null, null))).isEqualTo(RequestLimits.MAX_BODY_BYTES);
    }

    @Test
    @DisplayName("길이 모르는 본문이 1MiB를 1바이트라도 넘으면 읽는 도중 413 CONTENT_TOO_LARGE")
    void overLimit() throws Exception {
        HttpInputMessage limited = advice.beforeBodyRead(chunked(RequestLimits.MAX_BODY_BYTES + 1), null, null, null);
        ApiExceptionAssertions.assertErrorCode(() -> readAll(limited), ErrorCode.CONTENT_TOO_LARGE);
    }

    private static HttpInputMessage chunked(long size) {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[(int) size]);
            }

            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                return headers;
            }
        };
    }

    private static long readAll(HttpInputMessage message) throws Exception {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream in = message.getBody()) {
            for (int n; (n = in.read(buffer)) != -1; ) {
                total += n;
            }
        }
        return total;
    }
}
