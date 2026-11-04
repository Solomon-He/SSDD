package org.ssdd.task;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.ssdd.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class TaskRepository {
    public record Row(String id,String userId,String modelId,String requestKey,String digest,String snapshot,String manifest,
            int imageCount,String status,int attempts,int maxAttempts,String token,String outputs,int completed,String batchId,
            String error,Instant nextAttempt,Instant expires,Instant cleaned,Instant created,Instant updated,Instant started,Instant finished) {
        public boolean terminal(){return status.equals("succeeded")||status.equals("failed");}
    }
    private final JdbcTemplate jdbc;
    public TaskRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static Instant time(ResultSet r,String column) throws SQLException{var v=r.getObject(column,LocalDateTime.class);return v==null?null:v.toInstant(ZoneOffset.UTC);}
    private static final RowMapper<Row> MAPPER=(r,n)->new Row(r.getString("id"),r.getString("user_id"),r.getString("model_id"),r.getString("request_key"),r.getString("request_sha256"),r.getString("model_snapshot"),r.getString("input_manifest"),r.getInt("image_count"),r.getString("status"),r.getInt("attempts"),r.getInt("max_attempts"),r.getString("attempt_token"),r.getString("attempt_outputs"),r.getInt("completed_images"),r.getString("result_batch_id"),r.getString("error_code"),time(r,"next_attempt_at"),time(r,"expires_at"),time(r,"inputs_cleaned_at"),time(r,"created_at"),time(r,"updated_at"),time(r,"started_at"),time(r,"finished_at"));
    public Row find(String id){var rows=jdbc.query("SELECT * FROM detection_tasks WHERE id=?",MAPPER,id);return rows.isEmpty()?null:rows.get(0);}
    public Row byKey(String user,String key){if(key==null)return null;var rows=jdbc.query("SELECT * FROM detection_tasks WHERE user_id=? AND request_key=?",MAPPER,user,key);return rows.isEmpty()?null:rows.get(0);}
    public void lockControl(){jdbc.queryForObject("SELECT id FROM detection_task_control WHERE id=1 FOR UPDATE",Integer.class);}
    public long pendingCount(){return jdbc.queryForObject("SELECT COUNT(*) FROM detection_tasks WHERE status IN ('queued','running','retry_wait')",Long.class);}
    public boolean hasEligible(){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM detection_tasks WHERE status IN ('queued','retry_wait') AND (next_attempt_at<=UTC_TIMESTAMP(6) OR expires_at<=UTC_TIMESTAMP(6)))",Boolean.class));}
    public void capacity(int capacity){if(pendingCount()>=capacity)throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"异步队列已满，请稍后重试");}
    public void insert(String id,String user,String model,String key,String digest,String snapshot,String manifest,int count,int maxAttempts,Instant expires){
        jdbc.update("INSERT INTO detection_tasks(id,user_id,model_id,request_key,request_sha256,model_snapshot,input_manifest,image_count,max_attempts,attempt_outputs,expires_at) VALUES(?,?,?,?,?,?,?,?,?,'[]',?)",id,user,model,key,digest,snapshot,manifest,count,maxAttempts,Timestamp.from(expires));
    }
    public Row claim(){
        var candidates=jdbc.query("SELECT * FROM detection_tasks WHERE status IN ('queued','retry_wait') AND (next_attempt_at<=UTC_TIMESTAMP(6) OR expires_at<=UTC_TIMESTAMP(6)) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",MAPPER);
        if(candidates.isEmpty())return null;var row=candidates.get(0);
        if(!row.expires().isAfter(Instant.now())||row.attempts()>=row.maxAttempts()){failQueued(row.id(),row.attempts()>=row.maxAttempts()?"RETRY_EXHAUSTED":"TASK_EXPIRED");return null;}
        String token=UUID.randomUUID().toString();
        jdbc.update("UPDATE detection_tasks SET status='running',attempts=attempts+1,attempt_token=?,completed_images=0,error_code=NULL,started_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=?",token,row.id());return find(row.id());
    }
    public void requireAttempt(String id,String token){
        var matches=jdbc.query("SELECT id FROM detection_tasks WHERE id=? AND status='running' AND attempt_token=? FOR UPDATE",(r,n)->r.getString(1),id,token);
        if(matches.isEmpty())throw new TaskFault("STALE_ATTEMPT",false);
    }
    public void checkAttempt(String id,String token){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM detection_tasks WHERE id=? AND status='running' AND attempt_token=?)",Boolean.class,id,token)))throw new TaskFault("STALE_ATTEMPT",false);}
    public void checkpoint(String id,String token,int completed,String outputs){
        if(jdbc.update("UPDATE detection_tasks SET completed_images=?,attempt_outputs=?,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='running' AND attempt_token=?",completed,outputs,id,token)!=1)throw new TaskFault("STALE_ATTEMPT",false);
    }
    public void succeed(String id,String token,String batch){if(jdbc.update("UPDATE detection_tasks SET status='succeeded',completed_images=image_count,result_batch_id=?,error_code=NULL,finished_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='running' AND attempt_token=?",batch,id,token)!=1)throw new TaskFault("STALE_ATTEMPT",false);}
    public boolean failedAttempt(Row row,String code,boolean retry,Instant next){
        String status=retry&&row.attempts()<row.maxAttempts()&&next.isBefore(row.expires())?"retry_wait":"failed";
        return jdbc.update("UPDATE detection_tasks SET status=?,error_code=?,next_attempt_at=?,finished_at=IF(?='failed',UTC_TIMESTAMP(6),NULL),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='running' AND attempt_token=?",status,code,Timestamp.from(next),status,row.id(),row.token())==1;
    }
    public List<Row> interrupted(){return jdbc.query("SELECT * FROM detection_tasks WHERE status='running' ORDER BY created_at,id",MAPPER);}
    public void recover(Row row){
        boolean retry=row.attempts()<row.maxAttempts()&&row.expires().isAfter(Instant.now());
        jdbc.update("UPDATE detection_tasks SET status=?,attempt_token=NULL,completed_images=0,attempt_outputs='[]',error_code='WORKER_RESTARTED',next_attempt_at=UTC_TIMESTAMP(6),finished_at=IF(?=0,UTC_TIMESTAMP(6),NULL),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status='running' AND attempt_token=?",retry?"queued":"failed",retry?1:0,row.id(),row.token());
    }
    public List<Row> needsCleanup(){return jdbc.query("SELECT * FROM detection_tasks WHERE status IN ('succeeded','failed') AND inputs_cleaned_at IS NULL ORDER BY created_at,id LIMIT 100",MAPPER);}
    public void markCleaned(String id){jdbc.update("UPDATE detection_tasks SET inputs_cleaned_at=UTC_TIMESTAMP(6) WHERE id=? AND status IN ('succeeded','failed')",id);}
    public void clearOutputs(String id,String token){if(jdbc.update("UPDATE detection_tasks SET attempt_outputs='[]' WHERE id=? AND status='running' AND attempt_token=?",id,token)!=1)throw new TaskFault("STALE_ATTEMPT",false);}
    private void failQueued(String id,String code){jdbc.update("UPDATE detection_tasks SET status='failed',error_code=?,finished_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status IN ('queued','retry_wait')",code,id);}
}
