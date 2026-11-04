package org.ssdd.model;

import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.ssdd.common.exception.BusinessException;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class ModelRepository {
    public record State(String activeModelId,long version) {}
    public record ModelRow(String id,String name,String version,String fileName,String storageKey,String sha256,long bytes,
            String inferenceConfig,String description,String uploadedBy,Double miou,Double precision,Double recall,Double dice,
            Double fps,String metricSource,Instant createdAt,Instant updatedAt,Instant deletedAt) {}
    private final JdbcTemplate jdbc;
    public ModelRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static Instant instant(ResultSet row,String column) throws SQLException {
        var value=row.getObject(column,LocalDateTime.class); return value==null?null:value.toInstant(ZoneOffset.UTC);
    }
    private static final RowMapper<ModelRow> MAPPER=(r,n)->new ModelRow(r.getString("id"),r.getString("name"),r.getString("version"),
            r.getString("file_name"),r.getString("storage_key"),r.getString("file_sha256"),r.getLong("file_size_bytes"),
            r.getString("inference_config"),r.getString("description"),r.getString("uploaded_by"),r.getObject("miou",Double.class),
            r.getObject("precision_score",Double.class),r.getObject("recall_score",Double.class),r.getObject("dice",Double.class),
            r.getObject("fps",Double.class),r.getString("metric_source"),instant(r,"created_at"),instant(r,"updated_at"),instant(r,"deleted_at"));
    public State state() { return jdbc.queryForObject("SELECT active_model_id,state_version FROM model_runtime_state WHERE id=1",(r,n)->new State(r.getString(1),r.getLong(2))); }
    public State lockState() { return jdbc.queryForObject("SELECT active_model_id,state_version FROM model_runtime_state WHERE id=1 FOR UPDATE",(r,n)->new State(r.getString(1),r.getLong(2))); }
    public ModelRow find(String id) { return one("SELECT * FROM model_versions WHERE id=? AND deleted_at IS NULL",id); }
    public ModelRow lock(String id) { return one("SELECT * FROM model_versions WHERE id=? AND deleted_at IS NULL FOR UPDATE",id); }
    private ModelRow one(String sql,String id) { var rows=jdbc.query(sql,MAPPER,id); if(rows.isEmpty())throw ModelErrors.missing();return rows.get(0); }
    public List<ModelRow> list() {
        var rows=jdbc.query("SELECT * FROM model_versions WHERE deleted_at IS NULL ORDER BY created_at DESC,id DESC LIMIT 1001",MAPPER);
        if(rows.size()>1000)throw ModelErrors.bad("模型列表超过 1000 条，请先清理无用模型");return rows;
    }
    public void requireAdmin(String id) {
        var roles=jdbc.query("SELECT p.role FROM users u JOIN user_profiles p ON p.user_id=u.id WHERE u.id=? AND u.status='active' FOR UPDATE",(r,n)->r.getString(1),id);
        if(roles.isEmpty()||!roles.get(0).equals("admin"))throw new BusinessException(HttpStatus.FORBIDDEN,"权限不足");
    }
    public void insert(ModelRow row) {
        jdbc.update("""
                INSERT INTO model_versions(id,name,version,file_name,storage_key,file_sha256,file_size_bytes,inference_config,description,uploaded_by)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,row.id(),row.name(),row.version(),row.fileName(),row.storageKey(),row.sha256(),row.bytes(),row.inferenceConfig(),row.description(),row.uploadedBy());
    }
    public void activate(String id) { jdbc.update("UPDATE model_runtime_state SET active_model_id=?,state_version=state_version+1 WHERE id=1",id); }
    public void update(ModelRow row) {
        jdbc.update("""
                UPDATE model_versions SET name=?,version=?,description=?,miou=?,precision_score=?,recall_score=?,dice=?,fps=?,metric_source=?,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND deleted_at IS NULL
                """,row.name(),row.version(),row.description(),row.miou(),row.precision(),row.recall(),row.dice(),row.fps(),row.metricSource(),row.id());
    }
    public void softDelete(String id) { jdbc.update("UPDATE model_versions SET deleted_at=UTC_TIMESTAMP(6) WHERE id=?",id); }
    public boolean referenced(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM detection_records WHERE model_id=?) OR EXISTS(SELECT 1 FROM detection_batches WHERE model_id=?)",Boolean.class,id,id));
    }
}
