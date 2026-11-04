package org.ssdd.ratelimit;

import org.springframework.http.HttpStatus;
import org.ssdd.common.exception.BusinessException;

public class RateLimitException extends BusinessException {
    private final long retryAfter;
    public RateLimitException(long milliseconds) {
        super(HttpStatus.TOO_MANY_REQUESTS,"请求过于频繁，请稍后重试");
        retryAfter=Math.max(1,(milliseconds+999)/1000);
    }
    public long retryAfter() { return retryAfter; }
}
