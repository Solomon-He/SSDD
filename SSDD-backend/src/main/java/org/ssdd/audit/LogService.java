package org.ssdd.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.common.api.*;
import java.util.*;

@Service
public class LogService {
    private final LogRepository repository;
    private final ObjectMapper json;
    public LogService(LogRepository repository,ObjectMapper json){this.repository=repository;this.json=json;}
    @Transactional(readOnly=true)
    public PageResult<Map<String,Object>> list(SessionPrincipal actor,PageQuery page,LogRepository.Filter filters) {
        var scope=repository.scope(actor,filters);long count=repository.count(scope);
        var rows=repository.page(scope,((long)page.page()-1)*page.pageSize(),page.pageSize());
        return PageResult.of(rows.stream().map(r->view(r,actor.role().equals("admin"))).toList(),count,page.page(),page.pageSize());
    }
    private Map<String,Object> view(LogRepository.Row row,boolean admin) {
        var result=new LinkedHashMap<String,Object>();result.put("id",row.id());result.put("user_id",row.userId());result.put("action",row.action());
        try {result.put("details",row.details()==null?Map.of():json.readTree(row.details()));}catch(java.io.IOException ex){throw new IllegalStateException("Invalid persisted audit JSON",ex);}
        result.put("ip_address",row.ip());result.put("user_agent",row.agent());result.put("status",row.status());result.put("created_at",row.created());
        result.put("request_id",row.requestId());result.put("target_id",row.targetId());result.put("user",Map.of("display_name",row.name(),"email",admin?row.email():""));return result;
    }
}
