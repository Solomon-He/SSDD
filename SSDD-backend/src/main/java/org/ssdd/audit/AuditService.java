package org.ssdd.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final org.springframework.context.ApplicationEventPublisher events;
    public AuditService(JdbcTemplate jdbc,ObjectMapper json,org.springframework.context.ApplicationEventPublisher events) { this.jdbc=jdbc;this.json=json;this.events=events; }
    public void success(String userId,String action,Map<String,?> details) {
        write(userId,action,"success",details);
        if(java.util.Set.of("register","model_upload","model_delete","model_activate","detect","batch_detect").contains(action))events.publishEvent(new org.ssdd.cache.StatsChanged());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failure(String action,Map<String,?> details) { write(null,action,"failure",details); }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failure(String userId,String action,Map<String,?> details) { write(userId,action,"failure",details); }
    private void write(String userId,String action,String status,Map<String,?> details) {
        try {
            String requestId=MDC.get("requestId");
            var context=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();String ip=null,agent=null;
            if(context instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
                var request=attributes.getRequest();ip=request.getRemoteAddr();agent=request.getHeader("User-Agent");
                if(agent!=null){agent=agent.replaceAll("[\\p{Cntrl}]"," ");agent=agent.substring(0,Math.min(512,agent.length()));}
            }
            String target=null;for(String key:java.util.List.of("record_id","batch_id","model_id","target_user_id"))if(details.get(key) instanceof String value&&value.length()<=64){target=value;break;}
            jdbc.update("INSERT INTO operation_logs(id,user_id,action,status,details,request_id,ip_address,user_agent,target_id) VALUES(?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(),userId,action,status,json.writeValueAsString(details),
                    requestId==null?UUID.randomUUID().toString():requestId,ip,agent,target);
        } catch(JsonProcessingException ex) { throw new IllegalStateException("Audit details serialization failed",ex); }
    }
}
