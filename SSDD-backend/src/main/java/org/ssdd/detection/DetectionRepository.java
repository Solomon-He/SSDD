package org.ssdd.detection;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.ssdd.auth.SessionPrincipal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class DetectionRepository {
    public record Row(String id,String userId,String modelId,String requestKey,String digest,
            String originalKey,String maskKey,String overlayKey,String channelKeys,
            int width,int height,long totalPixels,long defectPixels,int defectCount,double millis,
            String modelName,String modelVersion,String config,Instant createdAt,String role,String displayName,String avatarKey) {}
    public record Stats(int classIndex,int regions,long pixels) {}
    public record Batch(String id,String userId,String modelId,String digest,String snapshot,int imageCount) {}
    public record Scope(String clause,List<Object> args) {}
    private static final String SELECT="""
            SELECT r.*,p.role,p.display_name,p.avatar_storage_key,u.email AS actor_email FROM detection_records r
            JOIN user_profiles p ON p.user_id=r.user_id
            JOIN users u ON u.id=r.user_id
            """;
    private static final RowMapper<Row> MAPPER=(r,n)->new Row(r.getString("id"),r.getString("user_id"),r.getString("model_id"),
            r.getString("request_key"),r.getString("request_sha256"),r.getString("original_storage_key"),r.getString("mask_storage_key"),
            r.getString("overlay_storage_key"),r.getString("channel_mask_keys"),r.getInt("original_width"),r.getInt("original_height"),
            r.getLong("total_pixels"),r.getLong("defect_pixels"),r.getInt("defect_count"),r.getDouble("inference_time_ms"),
            r.getString("model_name_snapshot"),r.getString("model_version_snapshot"),r.getString("inference_config_snapshot"),
            r.getObject("created_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getString("role"),r.getString("display_name"),r.getString("avatar_storage_key"));
    private final JdbcTemplate jdbc;
    public DetectionRepository(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    public Row find(String id) {var rows=jdbc.query(SELECT+" WHERE r.id=?",MAPPER,id);return rows.isEmpty()?null:rows.get(0);}
    public Row byKey(String user,String key) {
        if(key==null)return null;
        var rows=jdbc.query(SELECT+" WHERE r.user_id=? AND r.request_key=? AND r.batch_id IS NULL AND r.batch_item_index=0",MAPPER,user,key);
        return rows.isEmpty()?null:rows.get(0);
    }
    public void requireActiveUser(SessionPrincipal actor) {
        var rows=jdbc.query("SELECT id FROM users WHERE id=? AND status='active' FOR UPDATE",(r,n)->r.getString(1),actor.userId());
        if(rows.isEmpty())throw new org.ssdd.common.exception.BusinessException(org.springframework.http.HttpStatus.FORBIDDEN,"账号已停用");
    }
    public void insert(Row row,List<Stats> stats) {
        insert(row,stats,null,0);
    }
    public void insert(Row row,List<Stats> stats,String batchId,int index) {
        jdbc.update("""
                INSERT INTO detection_records(id,user_id,model_id,batch_id,batch_item_index,request_key,request_sha256,original_storage_key,mask_storage_key,
                overlay_storage_key,channel_mask_keys,original_width,original_height,total_pixels,defect_pixels,defect_count,
                inference_time_ms,model_name_snapshot,model_version_snapshot,inference_config_snapshot)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,row.id,row.userId,row.modelId,batchId,index,row.requestKey,row.digest,row.originalKey,row.maskKey,row.overlayKey,row.channelKeys,
                row.width,row.height,row.totalPixels,row.defectPixels,row.defectCount,row.millis,row.modelName,row.modelVersion,row.config);
        for(var s:stats)jdbc.update("INSERT INTO detection_class_stats(record_id,class_index,region_count,pixel_count) VALUES(?,?,?,?)",row.id,s.classIndex,s.regions,s.pixels);
    }
    public List<Stats> stats(String id) {return jdbc.query("SELECT class_index,region_count,pixel_count FROM detection_class_stats WHERE record_id=? ORDER BY class_index",(r,n)->new Stats(r.getInt(1),r.getInt(2),r.getLong(3)),id);}
    public Map<String,List<Stats>> statsFor(List<String> ids) {
        var result=new HashMap<String,List<Stats>>();
        for(int start=0;start<ids.size();start+=200) {
            var group=ids.subList(start,Math.min(ids.size(),start+200));String placeholders=String.join(",",Collections.nCopies(group.size(),"?"));
            jdbc.query("SELECT record_id,class_index,region_count,pixel_count FROM detection_class_stats WHERE record_id IN ("+placeholders+") ORDER BY record_id,class_index",
                    (org.springframework.jdbc.core.RowCallbackHandler)r->result.computeIfAbsent(r.getString(1),k->new ArrayList<>()).add(new Stats(r.getInt(2),r.getInt(3),r.getLong(4))),group.toArray());
        }
        return result;
    }
    public Batch batchByKey(String user,String key) {
        if(key==null)return null;
        var found=jdbc.query("SELECT id,user_id,model_id,request_sha256,model_snapshot,image_count FROM detection_batches WHERE user_id=? AND request_key=? AND status='succeeded'",
                (r,n)->new Batch(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getInt(6)),user,key);
        return found.isEmpty()?null:found.get(0);
    }
    public void insertBatch(String id,String user,String model,String key,String digest,String snapshot,int count) {
        jdbc.update("INSERT INTO detection_batches(id,user_id,model_id,request_key,request_sha256,model_snapshot,status,image_count,completed_at) VALUES(?,?,?,?,?,?,'succeeded',?,UTC_TIMESTAMP(6))",id,user,model,key,digest,snapshot,count);
    }
    public List<Row> batchRows(String id) {return jdbc.query(SELECT+" WHERE r.batch_id=? ORDER BY r.batch_item_index",MAPPER,id);}
    public Scope scope(SessionPrincipal actor,String user,String search) {
        return scope(actor,user,search,new org.ssdd.data.DateRange(null,null));
    }
    public Scope scope(SessionPrincipal actor,String user,String search,org.ssdd.data.DateRange dates) {
        var args=new ArrayList<Object>();String clause=" WHERE 1=1";
        if(!actor.role().equals("admin")) {clause+=" AND r.user_id=?";args.add(actor.userId());}
        else if(user!=null&&!user.isBlank()) {try {user=UUID.fromString(user).toString();}catch(IllegalArgumentException ex){throw DetectionErrors.bad("userId 必须是 UUID");}clause+=" AND r.user_id=?";args.add(user);}
        else if(search!=null&&!search.isBlank()) {
            if(search.length()>100)throw DetectionErrors.bad("搜索内容过长");
            // LOCATE treats %, _ and backslash as literal characters.
            clause+=" AND LOCATE(?,p.display_name)>0";args.add(search.trim());
        }
        clause+=dates.append("r.created_at",args);return new Scope(clause,args);
    }
    public long count(Scope scope) {return jdbc.queryForObject("SELECT COUNT(*) FROM detection_records r JOIN user_profiles p ON p.user_id=r.user_id"+scope.clause,Long.class,scope.args.toArray());}
    public List<Row> list(Scope scope,long offset,int limit) {
        var args=new ArrayList<>(scope.args);args.add(limit);args.add(offset);
        return jdbc.query(SELECT+scope.clause+" ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",MAPPER,args.toArray());
    }
    public record ExportRow(Row record,String email) {}
    public List<ExportRow> exportPage(Scope scope,long offset,int limit) {
        var args=new ArrayList<>(scope.args);args.add(limit);args.add(offset);
        return jdbc.query(SELECT+scope.clause+" ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",(r,n)->new ExportRow(MAPPER.mapRow(r,n),r.getString("actor_email")),args.toArray());
    }
    public String resourceKey(String id,String kind) {
        Row row=find(id);if(row==null)throw DetectionErrors.missing();
        // All generated keys have this server-owned layout; membership is checked in persisted columns.
        String key="results/"+id+"/"+kind+".png";
        if(key.equals(row.originalKey)||key.equals(row.maskKey)||key.equals(row.overlayKey))return key;
        if(kind.matches("class-[1-4]")&&row.channelKeys.contains('"'+key+'"'))return key;
        throw DetectionErrors.missing();
    }
}
