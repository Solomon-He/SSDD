package org.ssdd.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.ssdd.ratelimit.RateLimitProperties;
import java.util.*;

@Component
public class TaskProgress {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final TaskProperties config;
    private final String prefix;
    public TaskProgress(StringRedisTemplate redis,ObjectMapper json,TaskProperties config,RateLimitProperties namespace){this.redis=redis;this.json=json;this.config=config;this.prefix=namespace.namespace()+":tasks:progress:";}
    public String key(String id){return prefix+id;}
    private Map<String,Object> value(TaskRepository.Row row){return Map.of("task_id",row.id(),"attempt_token",Objects.toString(row.token(),""),"status",row.status(),"attempts",row.attempts(),"completed_images",row.completed());}
    public void store(TaskRepository.Row row){
        try{redis.opsForValue().set(key(row.id()),json.writeValueAsString(value(row)),config.progressTtl());}
        catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).debug("Task progress cache unavailable; MySQL state retained; task={}",row.id());}
    }
    public String source(TaskRepository.Row row){
        try{String raw=redis.opsForValue().get(key(row.id()));if(raw!=null&&json.readTree(raw).equals(json.valueToTree(value(row))))return "redis";}
        catch(Exception ignored){}
        store(row);return "mysql";
    }
}
