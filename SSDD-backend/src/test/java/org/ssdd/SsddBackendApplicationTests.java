package org.ssdd;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.ssdd.common.api.PageQuery;
import org.ssdd.common.api.PageResult;
import org.ssdd.support.MySqlTestDatabase;
import org.ssdd.support.UserPaginationMapper;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("mysqlAvailable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ssdd.bootstrap-admin.enabled=false",
        "ssdd.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@Transactional
class SsddBackendApplicationTests {
    private static MySqlTestDatabase database;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired UserPaginationMapper users;
    @Autowired TestRestTemplate http;

    static boolean mysqlAvailable() { return MySqlTestDatabase.available(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws Exception {
        database = MySqlTestDatabase.open();
        registry.add("ssdd.database.url", database::url);
        registry.add("ssdd.database.username", database::username);
        registry.add("ssdd.database.password", database::password);
    }

    @AfterAll
    static void cleanup() throws Exception { if (database != null) database.close(); }

    @Test
    void createsSchemaAndRestartDoesNotRepeatMigrations() {
        assertEquals("5", flyway.info().current().getVersion().toString());
        assertEquals(11, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history'", Integer.class));
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
        assertEquals("+00:00", jdbc.queryForObject("SELECT @@session.time_zone", String.class));
    }

    @Test
    void upgradesV1DatabaseToLatestWithoutLosingData() throws Exception {
        try (var upgrade = MySqlTestDatabase.open()) {
            Flyway first = Flyway.configure().dataSource(upgrade.url(), upgrade.username(), upgrade.password())
                    .cleanDisabled(true).target("1").load();
            assertEquals(1, first.migrate().migrationsExecuted);
            try (var connection = java.sql.DriverManager.getConnection(upgrade.url(), upgrade.username(), upgrade.password());
                 var statement = connection.prepareStatement("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)")) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, "upgrade@example.test");
                statement.setString(3, "test-hash");
                statement.executeUpdate();
            }
            Flyway latest = Flyway.configure().dataSource(upgrade.url(), upgrade.username(), upgrade.password()).cleanDisabled(true).load();
            assertEquals(4, latest.migrate().migrationsExecuted);
            try (var connection = java.sql.DriverManager.getConnection(upgrade.url(), upgrade.username(), upgrade.password());
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM users WHERE email='upgrade@example.test'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test
    void enforcesUniqueEmailRoleAndForeignKeysAndPreservesUnicode() {
        String user = insertUser("inspector@example.test");
        assertThrows(DataIntegrityViolationException.class, () -> insertUser("INSPECTOR@example.test"));
        String profile = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_profiles(id,user_id,display_name) VALUES(?,?,?)", profile, user, "质检员🙂");
        assertEquals("质检员🙂", jdbc.queryForObject("SELECT display_name FROM user_profiles WHERE id=?", String.class, profile));
        assertCheckViolation(() -> jdbc.update("UPDATE user_profiles SET role='owner' WHERE id=?", profile));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO user_profiles(id,user_id,display_name) VALUES(?,?,?)", UUID.randomUUID().toString(), UUID.randomUUID().toString(), "missing user"));
    }

    @Test
    void onlyAllowsV2ModelsAndOneRuntimePointer() {
        String uploader = insertUser("model-admin@example.test");
        String model = insertModel(uploader);
        assertCheckViolation(() -> jdbc.update("UPDATE model_versions SET num_classes=5 WHERE id=?", model));
        assertCheckViolation(() -> jdbc.update("UPDATE model_versions SET arch_version='v9' WHERE id=?", model));
        assertCheckViolation(() -> jdbc.update("INSERT INTO model_runtime_state(id) VALUES(2)"));
        jdbc.update("UPDATE model_runtime_state SET active_model_id=?, state_version=state_version+1 WHERE id=1", model);
        assertEquals(model, jdbc.queryForObject("SELECT active_model_id FROM model_runtime_state WHERE id=1", String.class));
    }

    @Test
    void enforcesRefreshHashUniquenessAndBatchLimits() {
        String user = insertUser("session@example.test");
        String session = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO auth_sessions(id,user_id,expires_at) VALUES(?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 7 DAY))", session, user);
        String token = "a".repeat(64);
        jdbc.update("INSERT INTO auth_refresh_tokens(id,session_id,token_hash,expires_at) VALUES(?,?,?,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 7 DAY))", UUID.randomUUID().toString(), session, token);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO auth_refresh_tokens(id,session_id,token_hash,expires_at) VALUES(?,?,?,UTC_TIMESTAMP())", UUID.randomUUID().toString(), session, token));
        String model = insertModel(user);
        assertCheckViolation(() -> jdbc.update("INSERT INTO detection_batches(id,user_id,model_id,model_snapshot,image_count) VALUES(?,?,?,'{}',21)", UUID.randomUUID().toString(), user, model));
    }

    @Test
    void protectsBatchIdempotencyAndDetectionPixelInvariants() {
        String user = insertUser("detections@example.test");
        String model = insertModel(user);
        jdbc.update("INSERT INTO detection_batches(id,user_id,model_id,model_snapshot,image_count,request_key) VALUES(?,?,?,'{}',20,'stable-batch-key')", UUID.randomUUID().toString(), user, model);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO detection_batches(id,user_id,model_id,model_snapshot,image_count,request_key) VALUES(?,?,?,'{}',20,'stable-batch-key')", UUID.randomUUID().toString(), user, model));
        String record = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO detection_records(id,user_id,model_id,request_key,
                  original_storage_key,mask_storage_key,overlay_storage_key,channel_mask_keys,
                  original_width,original_height,total_pixels,defect_pixels,defect_count,inference_time_ms,
                  model_name_snapshot,model_version_snapshot,inference_config_snapshot)
                VALUES(?,?,?,'stable-single-key','original','mask','overlay','["1","2","3","4"]',
                  1600,256,409600,0,0,1,'test','1.0.0','{}')
                """, record, user, model);
        assertCheckViolation(() -> jdbc.update("UPDATE detection_records SET defect_pixels=409601 WHERE id=?", record));
        assertCheckViolation(() -> jdbc.update("UPDATE detection_records SET total_pixels=1 WHERE id=?", record));
        assertCheckViolation(() -> jdbc.update("INSERT INTO detection_class_stats(record_id,class_index,region_count,pixel_count) VALUES(?,5,0,0)", record));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO detection_class_stats(record_id,class_index,region_count,pixel_count) VALUES(?,1,0,0)", UUID.randomUUID().toString()));
    }

    @Test
    void paginatesWithMybatisPlusAgainstRealMysql() {
        for (int i = 0; i < 7; i++) insertUser("page" + i + "@example.test");
        var query = new PageQuery(2, 3);
        var result = PageResult.from(users.pageUsers(query.toMybatisPage()));
        assertEquals(7, result.total());
        assertEquals(3, result.items().size());
        assertEquals(2, result.page());
        assertEquals(3, result.totalPages());
    }

    @Test
    void servesHealthReadinessOpenApiAndSafeNotFound() {
        var health = http.getForEntity("/health", Map.class);
        assertEquals(HttpStatus.OK, health.getStatusCode());
        assertEquals("ok", health.getBody().get("status"));
        assertFalse(health.getBody().containsKey("success"));
        assertNotNull(health.getHeaders().getFirst("X-Request-Id"));
        var readiness = http.getForEntity("/actuator/health/readiness", Map.class);
        assertEquals(HttpStatus.OK, readiness.getStatusCode());
        assertEquals("UP", readiness.getBody().get("status"));
        var openapi = http.getForEntity("/v3/api-docs", Map.class);
        assertEquals(HttpStatus.OK, openapi.getStatusCode());
        assertTrue(((Map<?, ?>) openapi.getBody().get("paths")).containsKey("/health"));
        var missing = http.getForEntity("/api/not-implemented", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatusCode());
        assertEquals(false, missing.getBody().get("success"));
        assertEquals("未认证或会话已过期", missing.getBody().get("error"));
    }

    private String insertUser(String email) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES(?,?,?)", id, email, "test-hash-not-a-real-account");
        return id;
    }

    private void assertCheckViolation(org.junit.jupiter.api.function.Executable action) {
        var ex = assertThrows(DataAccessException.class, action);
        Throwable root = ex.getMostSpecificCause();
        assertInstanceOf(java.sql.SQLException.class, root);
        assertEquals(3819, ((java.sql.SQLException) root).getErrorCode());
    }

    private String insertModel(String uploader) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO model_versions(id,name,version,file_name,storage_key,file_sha256,file_size_bytes,inference_config,uploaded_by) VALUES(?,?,?,?,?,?,?,'{}',?)", id, "Test V2", "1.0.0", "test.onnx", "models/" + id + ".onnx", "b".repeat(64), 1, uploader);
        return id;
    }
}
