package com.awesomedesk.j_planner.common.api;

import com.awesomedesk.j_planner.common.error.ApiException;
import com.awesomedesk.j_planner.common.error.ErrorCode;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * 길이를 모르는 본문(chunked 등 Content-Length 없음)도 1MiB에서 막는다 (US-41, PO #317 2번).
 * Content-Length가 있으면 {@link RequestLimits#bodySize()}가 본문을 읽기 전에 413을 주고, 없으면 JSON 본문을 읽는 동안 세다가 넘는 순간 413.
 */
@ControllerAdvice
public class BodySizeLimitAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        InputStream limited = new LimitedInputStream(inputMessage.getBody(), RequestLimits.MAX_BODY_BYTES);
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return limited;
            }

            @Override
            public HttpHeaders getHeaders() {
                return inputMessage.getHeaders();
            }
        };
    }

    /** {@code max}바이트를 넘게 읽으려 하면 413 */
    static final class LimitedInputStream extends FilterInputStream {

        private final long max;
        private long count;

        LimitedInputStream(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) {
            count += n;
            if (count > max) {
                throw new ApiException(ErrorCode.CONTENT_TOO_LARGE);
            }
        }
    }
}
