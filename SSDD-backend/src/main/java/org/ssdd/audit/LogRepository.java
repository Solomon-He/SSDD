package org.ssdd.audit;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.data.DateRange;
import org.ssdd.detection.DetectionErrors;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class LogRepository {
    public record Filter(String action,String userId,String search,String role,String startDate,String endDate) {}
    public record Scope(String sql,List<Object> args) {}
    public record Row(String id,String userId,String action,String details,String ip,String agent,String status,Instant created,String name,String email,String requestId,String targetId) {}
    private static final String FROM=" FROM operation_logs l LEFT JOIN users u ON u.id=l.user_id LEFT JOIN user_profiles p ON p.user_id=l.user_id";
    private static final RowMapper<Row> MAPPER=(r,n)->new Row(r.getString("id"),r.getString("user_id"),r.getString("action"),r.getString("details"),r.getString("ip_address"),r.getString("user_agent"),r.getString("status"),r.getObject("created_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),Objects.toString(r.getString("display_name"),"系统/未知用户"),Objects.toString(r.getString("email"),""),r.getString("request_id"),r.getString("target_id"));
    private final JdbcTemplate jdbc;
    public LogRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Scope scope(SessionPrincipal actor,Filter filter) {
        var args=new ArrayList<Object>();String sql=" WHERE 1=1";
        if(!actor.role().equals("admin")){sql+=" AND l.user_id=?";args.add(actor.userId());}
        else {
            if(filter.userId()!=null&&!filter.userId().isBlank()) {
                String user;try{user=UUID.fromString(filter.userId()).toString();}catch(IllegalArgumentException ex){throw DetectionErrors.bad("userId 必须是 UUID");}
                sql+=" AND l.user_id=?";args.add(user);
            } else if(filter.search()!=null&&!filter.search().isBlank()) {
                if(filter.search().length()>100)throw DetectionErrors.bad("搜索内容过长");
                sql+=" AND (LOCATE(?,p.display_name)>0 OR LOCATE(?,u.email)>0)";args.add(filter.search().trim());args.add(filter.search().trim());
            }
            if(filter.role()!=null&&!filter.role().isBlank()) {
                if(!Set.of("admin","inspector").contains(filter.role()))throw DetectionErrors.bad("无效的角色筛选");sql+=" AND p.role=?";args.add(filter.role());
            }
        }
        if(filter.action()!=null&&!filter.action().isBlank()) {
            if(!filter.action().matches("[a-z][a-z0-9_]{0,63}"))throw DetectionErrors.bad("无效的操作类型");sql+=" AND l.action=?";args.add(filter.action());
        }
        sql+=DateRange.parse(filter.startDate(),filter.endDate()).append("l.created_at",args);return new Scope(sql,args);
    }
    public long count(Scope scope){return jdbc.queryForObject("SELECT COUNT(*)"+FROM+scope.sql,Long.class,scope.args.toArray());}
    public List<Row> page(Scope scope,long offset,int limit) {
        var args=new ArrayList<>(scope.args);args.add(limit);args.add(offset);
        return jdbc.query("SELECT l.*,p.display_name,u.email"+FROM+scope.sql+" ORDER BY l.created_at DESC,l.id DESC LIMIT ? OFFSET ?",MAPPER,args.toArray());
    }
}
