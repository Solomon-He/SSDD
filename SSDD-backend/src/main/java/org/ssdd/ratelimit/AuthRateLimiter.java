package org.ssdd.ratelimit;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ssdd.auth.TokenService;
import org.ssdd.common.exception.BusinessException;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.List;

@Service
public class AuthRateLimiter {
    private static final DefaultRedisScript<Long> QUOTA=new DefaultRedisScript<>("""
            local count=redis.call('INCR',KEYS[1])
            if count==1 or redis.call('PTTL',KEYS[1])<0 then redis.call('PEXPIRE',KEYS[1],ARGV[2]) end
            if count>tonumber(ARGV[1]) then return math.max(1,redis.call('PTTL',KEYS[1])) end
            return 0
            """,Long.class);
    private static final DefaultRedisScript<Long> LOGIN=new DefaultRedisScript<>("""
            local blocked=redis.call('PTTL',KEYS[3])
            if blocked>0 then return blocked end
            local ip=redis.call('INCR',KEYS[1])
            if ip==1 or redis.call('PTTL',KEYS[1])<0 then redis.call('PEXPIRE',KEYS[1],ARGV[3]) end
            if ip>tonumber(ARGV[1]) then return math.max(1,redis.call('PTTL',KEYS[1])) end
            local account=redis.call('INCR',KEYS[2])
            if account==1 or redis.call('PTTL',KEYS[2])<0 then redis.call('PEXPIRE',KEYS[2],ARGV[3]) end
            if account>tonumber(ARGV[2]) then return math.max(1,redis.call('PTTL',KEYS[2])) end
            return 0
            """,Long.class);
    private static final DefaultRedisScript<Long> FAILURE=new DefaultRedisScript<>("""
            local failures=redis.call('INCR',KEYS[1])
            if failures==1 or redis.call('PTTL',KEYS[1])<0 then redis.call('PEXPIRE',KEYS[1],ARGV[2]) end
            if failures>=tonumber(ARGV[1]) then redis.call('SET',KEYS[2],'1','PX',ARGV[3]) end
            return failures
            """,Long.class);
    private final StringRedisTemplate redis;
    private final RateLimitProperties config;
    private final Environment environment;
    public AuthRateLimiter(StringRedisTemplate redis,RateLimitProperties config,Environment environment) {
        this.redis=redis;this.config=config;this.environment=environment;
    }
    @PostConstruct void validateMode() {
        if(!config.enabled()) {
            if(Arrays.asList(environment.getActiveProfiles()).contains("prod"))throw new IllegalStateException("生产环境必须启用认证限流");
            LoggerFactory.getLogger(getClass()).warn("Authentication rate limiting explicitly disabled for development/test");
        }
    }
    public void login(String email,String ip) {
        if(!config.enabled())return;
        execute(()->{
            Long retry=redis.execute(LOGIN,List.of(key("login-ip",ip),key("login-account",email),key("cooldown",email)),
                    Integer.toString(config.loginIpLimit()),Integer.toString(config.loginAccountLimit()),Long.toString(config.window().toMillis()));
            reject(retry);
        });
    }
    public void register(String ip) { quota("register-ip",ip,config.registerIpLimit()); }
    public void refresh(String ip) { quota("refresh-ip",ip,config.refreshIpLimit()); }
    private void quota(String category,String subject,int limit) {
        if(!config.enabled())return;
        execute(()->reject(redis.execute(QUOTA,List.of(key(category,subject)),Integer.toString(limit),Long.toString(config.window().toMillis()))));
    }
    public void failure(String email) {
        if(!config.enabled())return;
        execute(()->redis.execute(FAILURE,List.of(key("failures",email),key("cooldown",email)),
                Integer.toString(config.maxFailures()),Long.toString(config.window().toMillis()),Long.toString(config.cooldown().toMillis())));
    }
    public void success(String email) {
        if(!config.enabled())return;
        execute(()->redis.delete(List.of(key("failures",email),key("cooldown",email))));
    }
    public String key(String category,String subject) { return config.namespace()+":"+category+":"+TokenService.hash(subject); }
    private static void reject(Long retry) {
        if(retry==null)throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"限流服务暂不可用");
        if(retry>0) {
            LoggerFactory.getLogger(AuthRateLimiter.class).debug("Authentication request rate limited; retryAfterMs={}",retry);
            throw new RateLimitException(retry);
        }
    }
    private void execute(Runnable action) {
        try { action.run(); }
        catch(DataAccessException ex) {
            LoggerFactory.getLogger(getClass()).warn("Authentication rate-limit Redis operation unavailable; type={}",ex.getClass().getSimpleName());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"限流服务暂不可用");
        }
    }
}
