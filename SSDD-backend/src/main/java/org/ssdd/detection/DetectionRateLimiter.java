package org.ssdd.detection;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.ssdd.ratelimit.*;
import org.ssdd.auth.TokenService;
import org.ssdd.common.exception.BusinessException;
import java.util.*;

@Component
public class DetectionRateLimiter {
    private static final DefaultRedisScript<Long> QUOTA=new DefaultRedisScript<>("""
            local n=redis.call('INCR',KEYS[1])
            if n==1 or redis.call('PTTL',KEYS[1])<0 then redis.call('PEXPIRE',KEYS[1],ARGV[2]) end
            if n>tonumber(ARGV[1]) then return math.max(1,redis.call('PTTL',KEYS[1])) end
            return 0
            """,Long.class);
    private final StringRedisTemplate redis;
    private final DetectionProperties config;
    private final RateLimitProperties auth;
    private final Environment environment;
    public DetectionRateLimiter(StringRedisTemplate redis,DetectionProperties config,RateLimitProperties auth,Environment environment) {
        this.redis=redis;this.config=config;this.auth=auth;this.environment=environment;
    }
    @PostConstruct void mode() {
        if(!config.rateLimitEnabled()) {
            if(Arrays.asList(environment.getActiveProfiles()).contains("prod"))throw new IllegalStateException("生产环境必须启用检测限流");
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Detection rate limiting explicitly disabled for development/test");
        }
    }
    public String key(String userId) { return auth.namespace()+":detect-user:"+TokenService.hash(userId); }
    public void check(String userId) {
        if(!config.rateLimitEnabled())return;
        try {
            Long retry=redis.execute(QUOTA,List.of(key(userId)),Integer.toString(config.requestsPerWindow()),Long.toString(config.rateWindow().toMillis()));
            if(retry==null)throw unavailable();
            if(retry>0)throw new RateLimitException(retry);
        } catch(DataAccessException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Detection rate-limit Redis unavailable; type={}",ex.getClass().getSimpleName());
            throw unavailable();
        }
    }
    private BusinessException unavailable() { return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"检测限流服务暂不可用"); }
}
