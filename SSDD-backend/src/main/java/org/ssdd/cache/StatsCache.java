package org.ssdd.cache;

import com.fasterxml.jackson.databind.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import org.ssdd.data.StatsRepository;
import org.ssdd.ratelimit.RateLimitProperties;
import java.util.*;

@Service
public class StatsCache {
    private static final DefaultRedisScript<Long> STORE=new DefaultRedisScript<>("""
            if (redis.call('GET',KEYS[1]) or '')~=ARGV[1] then return 0 end
            redis.call('SET',KEYS[2],ARGV[2],'PX',ARGV[3]); return 1
            """,Long.class);
    private static final DefaultRedisScript<Long> INVALIDATE=new DefaultRedisScript<>("""
            redis.call('SET',KEYS[1],ARGV[1]);redis.call('DEL',KEYS[2]);return 1
            """,Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final StatsRepository source;
    private final StatsCacheProperties config;
    private final String dataKey,generationKey;
    public StatsCache(StringRedisTemplate redis,ObjectMapper json,StatsRepository source,StatsCacheProperties config,RateLimitProperties namespace) {
        this.redis=redis;this.json=json;this.source=source;this.config=config;
        dataKey=namespace.namespace()+":stats:v1:admin:global";generationKey=dataKey+":generation";
    }
    public String dataKey(){return dataKey;}
    public String generationKey(){return generationKey;}
    public StatsRepository.Summary get() {
        String generation="";boolean cacheAvailable=false;
        try {
            var values=redis.opsForValue().multiGet(List.of(generationKey,dataKey));
            if(values!=null&&values.size()==2) {
                generation=Objects.toString(values.get(0),"");cacheAvailable=true;
                if(values.get(1)!=null) {
                    try {var node=json.readTree(values.get(1));validate(node);var hit=json.treeToValue(node,StatsRepository.Summary.class);org.slf4j.LoggerFactory.getLogger(getClass()).debug("Stats cache hit; scope=admin-global");return hit;}
                    catch(Exception invalid) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Stats cache payload invalid; reading MySQL");}
                }
            }
        } catch(RuntimeException unavailable) {warn("read");}
        org.slf4j.LoggerFactory.getLogger(getClass()).debug("Stats cache miss; reading MySQL; scope=admin-global");
        var result=source.load();
        if(cacheAvailable)try {
            Long stored=redis.execute(STORE,List.of(generationKey,dataKey),generation,json.writeValueAsString(result),Long.toString(config.ttl().toMillis()));
            org.slf4j.LoggerFactory.getLogger(getClass()).debug("Stats cache refill; stored={}",Objects.equals(stored,1L));
        } catch(Exception unavailable) {warn("refill");}
        return result;
    }
    private void validate(JsonNode node) {
        if(node==null||!node.isObject()||node.size()!=7)throw new IllegalArgumentException();
        for(String key:List.of("total_users","total_detections","total_models","active_models","recent_detection_count"))if(!node.path(key).isIntegralNumber()||!node.path(key).canConvertToLong())throw new IllegalArgumentException();
        for(String key:List.of("avg_inference_time_ms","avg_defect_count"))if(!node.path(key).isNumber())throw new IllegalArgumentException();
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    public void invalidate(StatsChanged ignored) {
        try {redis.execute(INVALIDATE,List.of(generationKey,dataKey),UUID.randomUUID().toString());}
        catch(RuntimeException unavailable) {warn("invalidate-after-commit");}
    }
    private void warn(String operation) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Stats Redis {} unavailable; MySQL remains authoritative; cache TTL bounds staleness",operation);}
}
