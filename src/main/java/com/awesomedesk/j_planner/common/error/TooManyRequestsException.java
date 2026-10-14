package com.awesomedesk.j_planner.common.error;

import lombok.Getter;

/** 요청 횟수 제한에 걸림 → 429 TOO_MANY_REQUESTS + Retry-After(초) (08 13-7) */
@Getter
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(String detail, long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS, detail);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}
