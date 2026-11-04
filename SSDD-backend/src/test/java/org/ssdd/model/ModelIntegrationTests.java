package org.ssdd.model;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.LinkedMultiValueMap;
import org.ssdd.audit.AuditService;
import org.ssdd.auth.*;
import org.ssdd.inference.*;
import org.ssdd.storage.ModelStorage;
import org.ssdd.support.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIf("mysqlAvailable")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "ssdd.bootstrap-admin.enabled=false","ssdd.rate-limit.enabled=false","ssdd.auth.bcrypt-strength=10",
        "ssdd.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","ssdd.inference.device=cpu"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ModelIntegrationTests {
    private static final Path ROOT=Path.of("target/p3-model-test-"+UUID.randomUUID()).toAbsolutePath();
    private static MySqlTestDatabase database;
    @Autowired TestRestTemplate http;
    @Autowired ModelService models;
    @Autowired ModelRuntime runtime;
    @Autowired ModelRepository repository;
    @Autowired ModelStorage storage;
    @Autowired OnnxModelGuard guard;
    @Autowired AuthService auth;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired InferenceProperties properties;
    @MockitoSpyBean OrtInferenceSessionFactory factory;
    static boolean mysqlAvailable() { return MySqlTestDatabase.available(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
        database=MySqlTestDatabase.open();
        r.add("ssdd.database.url",database::url);r.add("ssdd.database.username",database::username);r.add("ssdd.database.password",database::password);
        r.add("ssdd.storage.root",ROOT::toString);
    }
    @AfterEach void resetFactory() { reset(factory); }
    @AfterAll static void cleanup(@Autowired ModelRuntime runtime) throws Exception {
        runtime.shutdown();
        if(database!=null)database.close();
        if(Files.exists(ROOT))try(var paths=Files.walk(ROOT)) { for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path); }
    }
    record Account(String id,String token) { @Override public String toString(){return "TestAccount[REDACTED]";} }
    private Account account(boolean admin) {
        String email=UUID.randomUUID()+"@example.test",password="P3-test-password!";
        var input=new AuthRequests.Register(email,password,"P3 tester");
        if(admin)auth.bootstrap(input);else auth.register(input);
        var login=auth.login(new AuthRequests.Login(email,password),"127.0.0.1","P3 tests");
        return new Account(login.user().id(),login.session().accessToken());
    }
    private ResponseEntity<JsonNode> call(String path,HttpMethod method,Object body,Account account) {
        var headers=new HttpHeaders();if(account!=null)headers.setBearerAuth(account.token());
        if(body!=null)headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private ResponseEntity<JsonNode> upload(Account actor,byte[] bytes,Map<String,String> extra) {
        var body=new LinkedMultiValueMap<String,Object>();
        body.add("model",new ByteArrayResource(bytes){@Override public String getFilename(){return "../../fixture.onnx";}});
        body.add("name","Test "+UUID.randomUUID());extra.forEach(body::set);
        var headers=new HttpHeaders();if(actor!=null)headers.setBearerAuth(actor.token());headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/models",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private String upload(Account actor) { var response=upload(actor,OnnxFixtures.valid(),Map.of());expect(response,201);return response.getBody().path("data").path("id").asText(); }
    private void activate(Account actor,String id) { expect(call("/api/models/"+id+"/activate",HttpMethod.PUT,null,actor),200); }
    private static void expect(ResponseEntity<?> response,int status) { assertEquals(status,response.getStatusCode().value()); }
    private Path file(String id) { return ROOT.resolve(repository.find(id).storageKey()); }
    private long files() throws Exception { var path=ROOT.resolve("models");if(!Files.exists(path))return 0;try(var paths=Files.list(path)){return paths.filter(Files::isRegularFile).count();} }

    @Test void authenticatesReadAndRestrictsAllWritesToAdministrators() {
        var admin=account(true);var inspector=account(false);
        expect(call("/api/models",HttpMethod.GET,null,null),401);
        expect(upload(inspector,OnnxFixtures.valid(),Map.of()),403);
        String id=upload(admin);
        expect(call("/api/models",HttpMethod.GET,null,inspector),200);
        expect(call("/api/models/"+id,HttpMethod.GET,null,inspector),200);
        for(var method:List.of(HttpMethod.PUT,HttpMethod.DELETE))expect(call("/api/models/"+id,method,method==HttpMethod.PUT?Map.of("name","evil"):null,inspector),403);
        expect(call("/api/models/"+id+"/activate",HttpMethod.PUT,null,inspector),403);
        var body=call("/api/models/"+id,HttpMethod.GET,null,admin).getBody().path("data");
        assertEquals("v2",body.path("arch_version").asText());assertEquals(4,body.path("num_classes").asInt());
        assertEquals("models/"+id+".onnx",body.path("file_path").asText());assertEquals("fixture.onnx",body.path("file_name").asText());
        assertEquals(OnnxFixtures.valid().length,body.path("file_size_bytes").asLong());assertTrue(body.path("miou").isNull());
        assertEquals("inactive",body.path("status").asText());assertFalse(body.toString().contains(ROOT.toString()));
        expect(call("/uploads/models/"+id+".onnx",HttpMethod.GET,null,admin),404);
    }
    @Test void rejectsInvalidGraphsArchitectureAndNonfiniteWarmupWithoutFilesOrRows() throws Exception {
        var admin=account(true);long before=files();
        for(byte[] bytes:List.of(OnnxFixtures.wrongChannels(),OnnxFixtures.external(),OnnxFixtures.nestedExternal(),OnnxFixtures.sigmoidOutput(),
                OnnxFixtures.customDomain(),OnnxFixtures.missingContract(),OnnxFixtures.nonfinite(),new byte[]{1,2,3}))expect(upload(admin,bytes,Map.of()),400);
        expect(upload(admin,OnnxFixtures.valid(),Map.of("arch_version","v1")),400);
        expect(upload(admin,OnnxFixtures.valid(),Map.of("num_classes","5")),400);
        assertEquals(before,files());
    }
    @Test void failedOperationAuditSurvivesBusinessRollbackWithoutSensitiveDetails() {
        var admin=account(true);
        expect(upload(admin,OnnxFixtures.wrongChannels(),Map.of()),400);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs WHERE user_id=? AND action='model_upload' AND status='failure'",Integer.class,admin.id()));
        String details=jdbc.queryForObject("SELECT details FROM operation_logs WHERE user_id=? AND action='model_upload' AND status='failure'",String.class,admin.id());
        assertTrue(details.contains("400"));assertFalse(details.contains(ROOT.toString()));assertFalse(details.contains(admin.token()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM model_versions WHERE uploaded_by=?",Integer.class,admin.id()));
    }
    @Test void ignoresClientFileSizeAndRequiresMetricProvenance() {
        var admin=account(true);String id=upload(admin);
        expect(call("/api/models/"+id,HttpMethod.PUT,Map.of("miou",.9),admin),400);
        expect(call("/api/models/"+id,HttpMethod.PUT,Map.of("miou",2,"metric_source","test-set"),admin),400);
        expect(call("/api/models/"+id,HttpMethod.PUT,Map.of("storage_key","../bad"),admin),400);
        var updated=call("/api/models/"+id,HttpMethod.PUT,Map.of("miou",.9,"metric_source","admin supplied; test dataset","file_size",999),admin);expect(updated,200);
        assertEquals(.9,updated.getBody().path("data").path("miou").asDouble());
        assertEquals(OnnxFixtures.valid().length,updated.getBody().path("data").path("file_size_bytes").asLong());
        var clear=new HashMap<String,Object>();clear.put("miou",null);clear.put("description",null);
        expect(call("/api/models/"+id,HttpMethod.PUT,clear,admin),200);
        assertNull(repository.find(id).miou());assertNull(repository.find(id).description());
    }
    @Test void activeLeasesKeepMetadataSnapshotAndOldNativeSessionUntilTheyClose() throws Exception {
        var admin=account(true);String first=upload(admin);activate(admin,first);
        try(var old=runtime.acquire()) {
            String oldName=old.model().name();
            expect(call("/api/models/"+first,HttpMethod.PUT,Map.of("name","New metadata"),admin),200);
            assertEquals(oldName,old.model().name());try(var fresh=runtime.acquire()){assertEquals("New metadata",fresh.model().name());}
            String second=upload(admin);activate(admin,second);
            expect(call("/api/models/"+second,HttpMethod.DELETE,null,admin),400);
            var oldFile=file(first);expect(call("/api/models/"+first,HttpMethod.DELETE,null,admin),200);
            assertTrue(Files.exists(oldFile));expect(call("/api/models/"+first,HttpMethod.GET,null,admin),404);
            assertEquals(first,old.model().id());assertEquals(4*256*1600,old.run(new float[OrtInferenceSessionFactory.INPUT_ELEMENTS]).length);
        }
        assertFalse(Files.exists(ROOT.resolve("models/"+first+".onnx")));
        assertEquals(0,((Number)runtime.status().get("retired_sessions")).intValue());
    }
    @Test void boundedNativeHandlesRejectAnotherCandidateUntilRetiredLeaseCloses() {
        var admin=account(true);String first=upload(admin);activate(admin,first);
        try(var old=runtime.acquire()) {
            String second=upload(admin);activate(admin,second);
            expect(upload(admin,OnnxFixtures.valid(),Map.of()),503);
            assertEquals(first,old.model().id());
        }
        expect(upload(admin,OnnxFixtures.valid(),Map.of()),201);
    }
    @Test void historicalReferencePublishedByOldLeasePreventsDeferredWeightDeletion() {
        var admin=account(true);String oldId=upload(admin);activate(admin,oldId);Path oldFile=file(oldId);
        try(var lease=runtime.acquire()) {
            String next=upload(admin);activate(admin,next);
            expect(call("/api/models/"+oldId,HttpMethod.DELETE,null,admin),200);
            assertTrue(Files.exists(oldFile));
            jdbc.update("INSERT INTO detection_batches(id,user_id,model_id,model_snapshot,image_count) VALUES(?,?,?,?,1)",
                    UUID.randomUUID().toString(),admin.id(),lease.model().id(),"{\"name\":\"in-flight snapshot\"}");
        }
        assertTrue(Files.exists(oldFile));
        assertEquals(0,((Number)runtime.status().get("retired_sessions")).intValue());
    }
    @Test void missingOrTamperedCandidatePreservesCurrentReadyHandle() throws Exception {
        var admin=account(true);String first=upload(admin);activate(admin,first);String candidate=upload(admin);
        Files.write(file(candidate),new byte[OnnxFixtures.valid().length]);
        expect(call("/api/models/"+candidate+"/activate",HttpMethod.PUT,null,admin),400);
        assertEquals(first,repository.state().activeModelId());assertEquals(true,runtime.status().get("ready"));
        try(var lease=runtime.acquire()){assertEquals(first,lease.model().id());}
    }
    @Test void failedDatabaseCommitClosesCandidateAndDoesNotPublishIt() throws Exception {
        var admin=account(true);String first=upload(admin);activate(admin,first);String second=upload(admin);
        var closed=new AtomicInteger();
        doAnswer(invocation->{
            InferenceSession nativeSession=(InferenceSession)invocation.callRealMethod();
            return new InferenceSession() {
                public DeviceInfo deviceInfo(){return nativeSession.deviceInfo();}
                public float[] run(float[] values){return nativeSession.run(values);}
                public void close(){closed.incrementAndGet();nativeSession.close();}
            };
        }).when(factory).openAndWarm(any());
        rejectAudit("model_activate");
        try { expect(call("/api/models/"+second+"/activate",HttpMethod.PUT,null,admin),500); }
        finally { jdbc.execute("DROP TRIGGER p3_reject_audit"); }
        assertEquals(1,closed.get());assertEquals(first,repository.state().activeModelId());
        try(var lease=runtime.acquire()){assertEquals(first,lease.model().id());assertEquals(4*256*1600,lease.run(new float[OrtInferenceSessionFactory.INPUT_ELEMENTS]).length);}
    }
    @Test void uploadDatabaseFailureCompensatesFileAndKeepsModelTableUnchanged() throws Exception {
        var admin=account(true);long count=files();int rows=jdbc.queryForObject("SELECT COUNT(*) FROM model_versions",Integer.class);
        rejectAudit("model_upload");
        try { expect(upload(admin,OnnxFixtures.valid(),Map.of()),500); }
        finally { jdbc.execute("DROP TRIGGER p3_reject_audit"); }
        assertEquals(count,files());assertEquals(rows,jdbc.queryForObject("SELECT COUNT(*) FROM model_versions",Integer.class));
    }
    @Test void metadataRollbackPreservesDatabaseAndRuntimeSnapshot() {
        var admin=account(true);String id=upload(admin);activate(admin,id);String name=repository.find(id).name();
        rejectAudit("model_update");
        try { expect(call("/api/models/"+id,HttpMethod.PUT,Map.of("name","Must rollback"),admin),500); }
        finally { jdbc.execute("DROP TRIGGER p3_reject_audit"); }
        assertEquals(name,repository.find(id).name());try(var lease=runtime.acquire()){assertEquals(name,lease.model().name());}
    }
    @Test void concurrentActivationIsBoundedAndDoesNotBlockExistingSnapshotDuringLoading() throws Exception {
        var admin=account(true);String old=upload(admin);activate(admin,old);String first=upload(admin),second=upload(admin);
        var loading=new CountDownLatch(1);var proceed=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        doAnswer(invocation->{loading.countDown();assertTrue(proceed.await(10,TimeUnit.SECONDS));return invocation.callRealMethod();}).when(factory).openAndWarm(any());
        try {
            var activation=pool.submit(()->call("/api/models/"+first+"/activate",HttpMethod.PUT,null,admin));
            assertTrue(loading.await(10,TimeUnit.SECONDS));try(var lease=runtime.acquire()){assertEquals(old,lease.model().id());}
            expect(call("/api/models/"+second+"/activate",HttpMethod.PUT,null,admin),503);
            proceed.countDown();expect(activation.get(15,TimeUnit.SECONDS),200);
            assertEquals(first,repository.state().activeModelId());
        } finally { proceed.countDown();pool.shutdownNow(); }
    }
    @Test void externalStateChangeRejectsStalePublishAndStaleSnapshotThenRecovers() throws Exception {
        var admin=account(true);String old=upload(admin);activate(admin,old);String next=upload(admin);
        var loading=new CountDownLatch(1);var proceed=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        doAnswer(invocation->{loading.countDown();assertTrue(proceed.await(10,TimeUnit.SECONDS));return invocation.callRealMethod();}).when(factory).openAndWarm(any());
        try {
            var activation=pool.submit(()->call("/api/models/"+next+"/activate",HttpMethod.PUT,null,admin));assertTrue(loading.await(10,TimeUnit.SECONDS));
            jdbc.update("UPDATE model_runtime_state SET state_version=state_version+1 WHERE id=1");
            proceed.countDown();expect(activation.get(15,TimeUnit.SECONDS),409);
            assertFalse((boolean)runtime.status().get("ready"));assertThrows(org.ssdd.common.exception.BusinessException.class,runtime::acquire);
        } finally { proceed.countDown();pool.shutdownNow();reset(factory);models.restore(); }
        assertTrue((boolean)runtime.status().get("ready"));assertEquals(old,repository.state().activeModelId());
    }
    @Test void roleRevokedDuringCandidateLoadingIsRecheckedBeforeCommit() throws Exception {
        var admin=account(true);String current=upload(admin);activate(admin,current);String next=upload(admin);
        doAnswer(invocation->{jdbc.update("UPDATE user_profiles SET role='inspector' WHERE user_id=?",admin.id());return invocation.callRealMethod();}).when(factory).openAndWarm(any());
        expect(call("/api/models/"+next+"/activate",HttpMethod.PUT,null,admin),403);
        assertEquals(current,repository.state().activeModelId());
    }
    @Test void historicalReferencesSurviveSoftDeleteAndKeepWeights() {
        var admin=account(true);String id=upload(admin);var path=file(id);String snapshot="{\"name\":\"historical name\"}";
        jdbc.update("INSERT INTO detection_batches(id,user_id,model_id,model_snapshot,image_count) VALUES(?,?,?,?,1)",UUID.randomUUID().toString(),admin.id(),id,snapshot);
        expect(call("/api/models/"+id,HttpMethod.PUT,Map.of("name","Renamed"),admin),200);
        expect(call("/api/models/"+id,HttpMethod.DELETE,null,admin),200);
        assertTrue(Files.exists(path));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM detection_batches WHERE model_id=? AND JSON_UNQUOTE(JSON_EXTRACT(model_snapshot,'$.name'))='historical name'",Integer.class,id));
        expect(call("/api/models/"+id+"/activate",HttpMethod.PUT,null,admin),404);
    }
    @Test void freshRuntimeRestoresPointerAndFailsSafelyIfFileIsMissing() throws Exception {
        var admin=account(true);String id=upload(admin);activate(admin,id);
        var fresh=new ModelRuntime(repository,properties);
        var service=new ModelService(repository,storage,guard,factory,fresh,audit,transactions,json);
        try { service.restore();assertTrue((boolean)fresh.status().get("ready"));try(var lease=fresh.acquire()){assertEquals(id,lease.model().id());} }
        finally { fresh.shutdown(); }
        var path=file(id);byte[] bytes=Files.readAllBytes(path);Files.delete(path);
        var failed=new ModelRuntime(repository,properties);
        try {
            new ModelService(repository,storage,guard,factory,failed,audit,transactions,json).restore();
            assertFalse((boolean)failed.status().get("ready"));assertEquals("MODEL_RESTORE_FAILED",failed.status().get("error_code"));
            assertEquals(id,repository.state().activeModelId());
            expect(call("/health",HttpMethod.GET,null,null),200);expect(call("/actuator/health/readiness",HttpMethod.GET,null,null),200);
        } finally { failed.shutdown();Files.write(path,bytes); }
    }
    private void rejectAudit(String action) {
        jdbc.execute("CREATE TRIGGER p3_reject_audit BEFORE INSERT ON operation_logs FOR EACH ROW BEGIN IF NEW.action='"+action+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P3 test database failure'; END IF; END");
    }
}
