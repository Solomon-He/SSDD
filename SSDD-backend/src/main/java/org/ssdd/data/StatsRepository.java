package org.ssdd.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class StatsRepository {
    public record Summary(@JsonProperty("total_users") long users,@JsonProperty("total_detections") long detections,
            @JsonProperty("total_models") long models,@JsonProperty("active_models") long active,
            @JsonProperty("recent_detection_count") long recent,@JsonProperty("avg_inference_time_ms") double millis,
            @JsonProperty("avg_defect_count") double defects) {
        public Summary {
            if(users<0||detections<0||models<0||active<0||active>1||active>models||recent<0||recent>100||recent>detections
                    ||!Double.isFinite(millis)||millis<0||!Double.isFinite(defects)||defects<0)throw new IllegalArgumentException("Invalid statistics DTO");
        }
    }
    private final JdbcTemplate jdbc;
    public StatsRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(readOnly=true)
    public Summary load() {
        // A single statement gives counts and recent sample the same database snapshot.
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM users) AS users,
                (SELECT COUNT(*) FROM detection_records) AS detections,
                (SELECT COUNT(*) FROM model_versions WHERE deleted_at IS NULL) AS models,
                (SELECT COUNT(*) FROM model_runtime_state s JOIN model_versions m ON m.id=s.active_model_id WHERE m.deleted_at IS NULL) AS active,
                COUNT(*) AS recent,COALESCE(AVG(inference_time_ms),0) AS millis,COALESCE(AVG(defect_count),0) AS defects
                FROM (SELECT inference_time_ms,defect_count FROM detection_records ORDER BY created_at DESC,id DESC LIMIT 100) recent
                """,(r,n)->new Summary(r.getLong("users"),r.getLong("detections"),r.getLong("models"),r.getLong("active"),r.getLong("recent"),r.getDouble("millis"),r.getDouble("defects")));
    }
}
