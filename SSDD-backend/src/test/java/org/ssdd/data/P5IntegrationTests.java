package org.ssdd.data;

import com.fasterxml.jackson.databind.*;
import org.apache.commons.csv.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.ssdd.audit.*;
import org.ssdd.auth.*;
import org.ssdd.cache.*;
import org.ssdd.detection.*;
import org.ssdd.inference.*;
import org.ssdd.model.*;
import org.ssdd.ratelimit.*;
import org.ssdd.storage.DetectionStorage;
import org.ssdd.support.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.ssdd.inference.DetectionPipelineTests.png;

@EnabledIf("mysqlAvailable")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"ssdd.bootstrap-admin.enabled=false",
        "ssdd.rate-limit.enabled=false","ssdd.detection.rate-limit-enabled=false","ssdd.stats-cache.ttl=2s",
        "ssdd.auth.bcrypt-strength=10","ssdd.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","ssdd.inference.device=cpu"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class P5IntegrationTests {
    private static final Path ROOT=Path.of("target/p5-integration-"+UUID.randomUUID()).toAbsolutePath();
    private static final String NAMESPACE="ssdd:p5-test:"+UUID.randomUUID();
    private static MySqlTestDatabase database;
    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;
    @Autowired ModelService models;
    @Autowired DetectionService detection;
    @MockitoSpyBean DetectionRepository records;
    @MockitoSpyBean LogRepository logRepository;
    @Autowired ModelRepository modelRepository;
    @Autowired StatsCache cache;
    @Autowired AuditService audit;
    @Autowired BatchProperties batchConfig;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean InferenceSessionFactory factory;
    @MockitoSpyBean ModelRuntime runtime;
    @MockitoSpyBean DetectionStorage files;
    @MockitoSpyBean StringRedisTemplate redis;
    @MockitoSpyBean StatsRepository source;
    @MockitoSpyBean DetectionRateLimiter limiter;
    @AfterEach void resetSpies(){reset(factory,runtime,files,redis,source,limiter,records,logRepository);}
    static boolean mysqlAvailable(){return MySqlTestDatabase.available();}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) throws Exception {
        database=MySqlTestDatabase.open();r.add("ssdd.database.url",database::url);r.add("ssdd.database.username",database::username);r.add("ssdd.database.password",database::password);
        r.add("ssdd.storage.root",ROOT::toString);r.add("ssdd.rate-limit.namespace",()->NAMESPACE);
    }
    @AfterAll static void cleanup(@Autowired ModelRuntime runtime,@Autowired StringRedisTemplate redis) throws Exception {
        runtime.shutdown();try(var keys=redis.scan(ScanOptions.scanOptions().match(NAMESPACE+":*").count(100).build())){while(keys.hasNext())redis.delete(keys.next());}
        finally{if(database!=null)database.close();if(Files.exists(ROOT))try(var paths=Files.walk(ROOT)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    record Account(String id,String token,String role){SessionPrincipal principal(){return new SessionPrincipal(id,"test-session",role);}@Override public String toString(){return "Account[REDACTED]";}}
    private Account account(boolean admin){return account(admin,"P5 tester");}
    private Account account(boolean admin,String name){String email=UUID.randomUUID()+"@example.test",password="P5-test-password!";var request=new AuthRequests.Register(email,password,name);if(admin)auth.bootstrap(request);else auth.register(request);var result=auth.login(new AuthRequests.Login(email,password),"127.0.0.1","P5 tests");return new Account(result.user().id(),result.session().accessToken(),admin?"admin":"inspector");}
    private String model(Account admin){return models.upload(admin.id(),new MockMultipartFile("model","v2.onnx","application/octet-stream",OnnxFixtures.valid()),"P5 "+UUID.randomUUID(),"2",null,"v2",4).get("id").toString();}
    private String ready(Account admin){String id=model(admin);models.activate(admin.id(),id);return id;}
    private ResponseEntity<JsonNode> get(Account actor,String path){return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers(actor)),JsonNode.class);}
    private HttpHeaders headers(Account actor){var headers=new HttpHeaders();if(actor!=null)headers.setBearerAuth(actor.token());return headers;}
    private ResponseEntity<byte[]> bytes(Account actor,String path){return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers(actor)),byte[].class);}
    private static JsonNode data(ResponseEntity<JsonNode> response){assertEquals(200,response.getStatusCode().value(),()->String.valueOf(response.getBody()));return response.getBody().path("data");}
    private ResponseEntity<JsonNode> batch(Account actor,List<byte[]> images,List<String> names,String key){return upload(actor,images,names,key,true);}
    private ResponseEntity<JsonNode> single(Account actor,byte[] image,String key){return upload(actor,List.of(image),List.of("image.png"),key,false);}
    private ResponseEntity<JsonNode> upload(Account actor,List<byte[]> images,List<String> names,String key,boolean batch){
        var headers=headers(actor);headers.setContentType(MediaType.MULTIPART_FORM_DATA);headers.set("User-Agent","P5 HTTP test");if(key!=null)headers.set("Idempotency-Key",key);
        var body=new LinkedMultiValueMap<String,Object>();for(int i=0;i<images.size();i++){String name=names.get(i);body.add(batch?"images":"image",new ByteArrayResource(images.get(i)){@Override public String getFilename(){return name;}});}
        return http.exchange(batch?"/api/detect/batch":"/api/detect",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private long rows(){return jdbc.queryForObject("SELECT COUNT(*) FROM detection_records",Long.class);}
    private long batches(){return jdbc.queryForObject("SELECT COUNT(*) FROM detection_batches",Long.class);}
    private long fileCount() throws Exception{Path dir=ROOT.resolve("results");if(!Files.exists(dir))return 0;try(var paths=Files.walk(dir)){return paths.filter(Files::isRegularFile).count();}}
    private List<CSVRecord> csv(ResponseEntity<byte[]> response) throws Exception {
        assertEquals(200,response.getStatusCode().value());assertEquals("text/csv;charset=UTF-8",response.getHeaders().getContentType().toString());assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("attachment"));
        byte[] body=response.getBody();assertArrayEquals(new byte[]{(byte)239,(byte)187,(byte)191},Arrays.copyOf(body,3));try(var parser=CSVParser.parse(new String(body,3,body.length-3,StandardCharsets.UTF_8),CSVFormat.RFC4180)){return parser.getRecords();}
    }
    private void noExportFiles() throws Exception{Path dir=ROOT.resolve("exports");if(Files.exists(dir))try(var paths=Files.list(dir)){assertEquals(0,paths.count());}}

    @Test void acceptsOneAndTwentyRejectsZeroTwentyOneAndInvalidTailBeforeNativeWork() throws Exception {
        var admin=account(true);String model=ready(admin);byte[] image=png(20,10);long before=rows(),batchBefore=batches();
        assertEquals(401,batch(null,List.of(image),List.of("a.png"),null).getStatusCode().value());
        for(int n:List.of(1,20)){var result=data(batch(admin,Collections.nCopies(n,image),Collections.nCopies(n,"same.png"),null));assertEquals(n,result.path("total").asInt());assertEquals(model,result.path("model").path("id").asText());for(var item:result.path("results")){assertEquals("same.png",item.path("file_name").asText());assertEquals(model,item.path("inference_config_snapshot").path("model_id").asText());}
            if(n==20){String second=result.path("results").get(1).path("record_id").asText();assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE detection_records SET batch_item_index=0 WHERE id=?",second));}
        }
        assertEquals(before+21,rows());assertEquals(batchBefore+2,batches());assertEquals(400,batch(admin,List.of(),List.of(),null).getStatusCode().value());assertEquals(400,batch(admin,Collections.nCopies(21,image),Collections.nCopies(21,"x"),null).getStatusCode().value());
        clearInvocations(runtime);long filesBefore=fileCount();assertEquals(400,batch(admin,List.of(image,new byte[]{1,2}),List.of("x","bad"),null).getStatusCode().value());verify(runtime,never()).acquire();assertEquals(before+21,rows());assertEquals(filesBefore,fileCount());
    }
    @Test void batchIdempotencyIncludesOrderAndFilenameAndIsIndependentFromSingleScope() throws Exception {
        var admin=account(true);ready(admin);var other=account(false);String key=UUID.randomUUID().toString();byte[] a=png(20,10),b=png(21,11);long before=rows();
        var first=data(batch(admin,List.of(a,b),List.of("../a.png","b.png"),key));var replay=data(batch(admin,List.of(a,b),List.of("a.png","b.png"),key));assertEquals(first.path("batch_id"),replay.path("batch_id"));assertEquals(first.path("results").get(0).path("record_id"),replay.path("results").get(0).path("record_id"));assertEquals(before+2,rows());
        assertEquals(409,batch(admin,List.of(b,a),List.of("b.png","a.png"),key).getStatusCode().value());assertEquals(409,batch(admin,List.of(a,b),List.of("renamed.png","b.png"),key).getStatusCode().value());
        assertNotEquals(first.path("batch_id"),data(batch(other,List.of(a,b),List.of("a.png","b.png"),key)).path("batch_id"));data(single(admin,a,key));assertEquals(before+5,rows());
    }
    @Test void nativeFailureOnSecondImageDiscardsWholeBatchAndReleasesCapacity() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),batchBefore=batches(),filesBefore=fileCount();
        var lease=mock(ModelRuntime.Lease.class);var actual=modelRepository.find(runtime.status().get("active_model_id").toString());when(lease.model()).thenReturn(actual);when(lease.device()).thenReturn(new InferenceSession.DeviceInfo("cpu","cpu",null,"test"));
        float[] output=new float[4*1600*256];Arrays.fill(output,-1);when(lease.runTimed(any())).thenReturn(new InferenceSession.RunResult(output,1)).thenThrow(DetectionErrors.busy());doReturn(lease).when(runtime).acquire();
        assertEquals(503,batch(admin,Collections.nCopies(2,png(20,10)),List.of("a","b"),null).getStatusCode().value());verify(lease).close();assertEquals(before,rows());assertEquals(batchBefore,batches());assertEquals(filesBefore,fileCount());
        reset(runtime);data(batch(admin,List.of(png(20,10)),List.of("again"),null));
    }
    @Test void databaseFailureAfterFirstRecordAndAuditFailureRollbackAllRowsAndFiles() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),batchBefore=batches(),filesBefore=fileCount();
        for(String trigger:List.of("CREATE TRIGGER p5_fail BEFORE INSERT ON detection_records FOR EACH ROW BEGIN IF NEW.batch_item_index=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P5 rollback'; END IF; END",
                "CREATE TRIGGER p5_fail BEFORE INSERT ON operation_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P5 audit rollback'")) {
            jdbc.execute(trigger);try{assertTrue(batch(admin,Collections.nCopies(2,png(20,10)),List.of("a","b"),null).getStatusCode().is5xxServerError());}finally{jdbc.execute("DROP TRIGGER p5_fail");}
            assertEquals(before,rows());assertEquals(batchBefore,batches());assertEquals(filesBefore,fileCount());
        }
    }
    @Test void storageFailureOnSecondImageCompensatesEarlierPreparedImage() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),filesBefore=fileCount();var count=new AtomicInteger();
        doAnswer(call->{if(count.incrementAndGet()==2)throw DetectionErrors.busy();return call.callRealMethod();}).when(files).save(any(),any());
        assertEquals(503,batch(admin,Collections.nCopies(2,png(20,10)),List.of("a","b"),null).getStatusCode().value());assertEquals(before,rows());assertEquals(filesBefore,fileCount());
    }
    @Test void batchKeepsOriginalModelAcrossSwitchDeleteAndRejectsConcurrentSubmissions() throws Exception {
        var admin=account(true);String old=model(admin),next=model(admin);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var closed=new AtomicBoolean();var calls=new AtomicInteger();
        var fake=new InferenceSession(){public DeviceInfo deviceInfo(){return new DeviceInfo("auto","cpu","CUDA_INITIALIZATION_FAILED","test");}public float[] run(float[] input){if(calls.getAndIncrement()==0){entered.countDown();try{assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException ex){throw new RuntimeException(ex);}}float[] output=new float[4*1600*256];Arrays.fill(output,-1);return output;}public void close(){closed.set(true);}};
        doReturn(fake).when(factory).openAndWarm(any());models.activate(admin.id(),old);reset(factory);var pool=Executors.newSingleThreadExecutor();String key=UUID.randomUUID().toString();byte[] bytes=png(20,10);
        try{var future=pool.submit(()->batch(admin,Collections.nCopies(2,bytes),List.of("a","b"),key));assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(409,batch(admin,Collections.nCopies(2,bytes),List.of("a","b"),key).getStatusCode().value());assertEquals(503,single(admin,bytes,null).getStatusCode().value());models.activate(admin.id(),next);models.delete(admin.id(),old);assertFalse(closed.get());release.countDown();var result=data(future.get(15,TimeUnit.SECONDS));assertEquals(old,result.path("model").path("id").asText());for(var item:result.path("results"))assertEquals(old,item.path("inference_config_snapshot").path("model_id").asText());assertTrue(closed.get());assertTrue(Files.exists(ROOT.resolve("models/"+old+".onnx")));}
        finally{release.countDown();pool.shutdownNow();}
    }
    @Test void batchTotalBudgetExpiresWithoutPublishingAndUserDisableIsRechecked() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),filesBefore=fileCount();var active=modelRepository.find(runtime.status().get("active_model_id").toString());var lease=mock(ModelRuntime.Lease.class);when(lease.model()).thenReturn(active);when(lease.device()).thenReturn(new InferenceSession.DeviceInfo("cpu","cpu",null,"test"));
        when(lease.runTimed(any())).thenAnswer(call->{Thread.sleep(1100);return new InferenceSession.RunResult(new float[4*1600*256],1100);});doReturn(lease).when(runtime).acquire();org.springframework.test.util.ReflectionTestUtils.setField(detection,"batches",new BatchProperties(20,Duration.ofSeconds(1)));
        try{assertEquals(504,batch(admin,List.of(png(20,10)),List.of("slow"),null).getStatusCode().value());}finally{org.springframework.test.util.ReflectionTestUtils.setField(detection,"batches",batchConfig);}
        assertEquals(before,rows());assertEquals(filesBefore,fileCount());var user=account(false);doAnswer(call->{jdbc.update("UPDATE users SET status='disabled' WHERE id=?",user.id());float[] output=new float[4*1600*256];Arrays.fill(output,-1);return new InferenceSession.RunResult(output,1);}).when(lease).runTimed(any());
        assertEquals(403,batch(user,List.of(png(20,10)),List.of("disabled"),null).getStatusCode().value());assertEquals(before,rows());assertEquals(filesBefore,fileCount());
    }
    @Test void batchUsesSameRateLimiterAsSingleAndAdmissionFailureNeverAcquiresLease() throws Exception {
        var admin=account(true);ready(admin);clearInvocations(runtime);doThrow(new RateLimitException(1000)).when(limiter).check(admin.id());var response=batch(admin,List.of(png(20,10)),List.of("a"),null);assertEquals(429,response.getStatusCode().value());assertNotNull(response.getHeaders().getFirst("Retry-After"));verify(runtime,never()).acquire();
    }
    @Test void detectionCsvAndListShareScopeDateRangeAndSnapshotNamesWithoutSignedUrls() throws Exception {
        var admin=account(true);String model=ready(admin);var user=account(false,"=SUM(1,2)\"材料\"");var other=account(false);var result=data(single(user,png(30,15),null));data(single(other,png(30,15),null));String id=result.path("record_id").asText();
        jdbc.update("UPDATE detection_records SET created_at='2026-11-04 12:00:00.000000' WHERE id=?",id);
        models.update(admin.id(),model,Map.of("name",json.getNodeFactory().textNode("Renamed")));
        var own=csv(bytes(user,"/api/detect/records/export?userId="+other.id()));assertEquals(2,own.size());assertFalse(own.get(0).toList().contains("邮箱"));assertTrue(own.get(1).get(1).startsWith("'="));assertEquals(result.path("model").path("name").asText(),own.get(1).get(2));assertFalse(own.toString().contains("signature="));
        String filter="userId="+user.id()+"&startDate=2026-11-04T12:00:00Z&endDate=2026-11-04T12:00:00Z";
        var all=csv(bytes(admin,"/api/detect/records/export?"+filter));assertTrue(all.get(0).toList().contains("邮箱"));assertEquals(2,all.size());assertEquals(1,data(get(admin,"/api/detect/records?"+filter)).path("total").asInt());
        assertEquals(1,csv(bytes(admin,"/api/detect/records/export?userId="+user.id()+"&startDate=2026-11-05T00:00:00Z")).size());assertEquals(400,bytes(admin,"/api/detect/records/export?startDate=bad").getStatusCode().value());noExportFiles();
    }
    @Test void logsFilterByOwnerActionRoleSearchDatesAndMaskEmailForInspectors() throws Exception {
        var admin=account(true);var user=account(false);var other=account(false);audit.failure(user.id(),"p5_own",Map.of("safe","quoted,\"text\"\nline"));audit.failure(other.id(),"p5_own",Map.of());audit.failure("p5_system",Map.of());
        var own=data(get(user,"/api/logs?action=p5_own&userId="+other.id()+"&search=nonexistent&role=admin"));assertEquals(1,own.path("total").asInt());var item=own.path("items").get(0);assertEquals("",item.path("user").path("email").asText());assertEquals(user.id(),item.path("user_id").asText());
        var all=data(get(admin,"/api/logs?action=p5_own&role=inspector"));assertEquals(2,all.path("total").asInt());assertTrue(all.path("items").get(0).path("user").path("email").asText().contains("@"));
        assertEquals(1,data(get(admin,"/api/logs?action=p5_system")).path("items").size());assertTrue(data(get(admin,"/api/logs?action=p5_system")).path("items").get(0).path("user_id").isNull());
        assertEquals(0,data(get(admin,"/api/logs?search=%25_&action=p5_own")).path("total").asInt());assertEquals(400,get(admin,"/api/logs?role=evil").getStatusCode().value());assertEquals(400,get(admin,"/api/logs?startDate=2026-11-05T00:00:00Z&endDate=2026-11-04T00:00:00Z").getStatusCode().value());
        var exported=csv(bytes(user,"/api/logs/export?action=p5_own&userId="+other.id()));assertEquals(2,exported.size());assertFalse(exported.get(0).toList().contains("邮箱"));assertTrue(exported.get(1).toString().contains("quoted"));assertEquals(2,csv(bytes(admin,"/api/logs/export?action=p5_own&userId="+user.id())).size());noExportFiles();
    }
    @Test void httpAuditsCaptureBoundedAgentDirectIpRequestIdAndNeverSensitiveContent() throws Exception {
        var admin=account(true);ready(admin);var result=data(batch(admin,List.of(png(20,10)),List.of("file.png"),null));var log=data(get(admin,"/api/logs?action=batch_detect&userId="+admin.id())).path("items").get(0);
        assertEquals("P5 HTTP test",log.path("user_agent").asText());assertEquals("127.0.0.1",log.path("ip_address").asText());assertEquals(result.path("batch_id"),log.path("target_id"));assertFalse(log.path("request_id").asText().isBlank());assertFalse(log.toString().contains("signature="));assertFalse(log.toString().contains("P5-test-password"));
    }
    @Test void statsAreAdminOnlyAndMatchCountsLastHundredAndSoftDeleteRules() throws Exception {
        var admin=account(true);var user=account(false);String id=ready(admin);String spare=model(admin);
        for(int i=0;i<4;i++)data(single(user,png(20,10),null));
        var result=data(get(admin,"/api/admin/stats"));assertEquals(rows(),result.path("total_detections").asLong());assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class),result.path("total_users").asLong());assertEquals(1,result.path("active_models").asInt());assertEquals(source.load().millis(),result.path("avg_inference_time_ms").asDouble(),.000001);
        assertEquals(403,get(user,"/api/admin/stats").getStatusCode().value());assertEquals(401,get(null,"/api/admin/stats").getStatusCode().value());models.delete(admin.id(),spare);assertEquals(source.load().models(),data(get(admin,"/api/admin/stats")).path("total_models").asLong());assertEquals(id,runtime.status().get("active_model_id"));
    }
    @Test void cacheHitTtlMalformedPayloadAndRedisOutageUseAuthoritativeDatabase() throws Exception {
        var admin=account(true);cache.invalidate(new StatsChanged());clearInvocations(source);cache.get();cache.get();verify(source,times(1)).load();assertTrue(redis.getExpire(cache.dataKey(),TimeUnit.MILLISECONDS)>0);
        redis.opsForValue().set(cache.dataKey(),"{\"unexpected\":42}",Duration.ofSeconds(2));cache.get();verify(source,times(2)).load();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);while(Boolean.TRUE.equals(redis.hasKey(cache.dataKey()))&&System.nanoTime()<deadline)Thread.sleep(50);cache.get();verify(source,times(3)).load();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("P5 injected Redis failure")).when(redis).opsForValue();var result=data(get(admin,"/api/admin/stats"));assertEquals(rows(),result.path("total_detections").asLong());
    }
    @Test void invalidationOccursAfterCommitNeverAfterRollbackAndUsesNamespacedAdminKey() {
        var admin=account(true);cache.get();String before=redis.opsForValue().get(cache.generationKey());var tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status->{audit.success(admin.id(),"register",Map.of());assertNotNull(redis.opsForValue().get(cache.dataKey()));status.setRollbackOnly();});assertEquals(before,redis.opsForValue().get(cache.generationKey()));assertNotNull(redis.opsForValue().get(cache.dataKey()));
        tx.executeWithoutResult(status->{audit.success(admin.id(),"register",Map.of());assertNotNull(redis.opsForValue().get(cache.dataKey()));});assertNull(redis.opsForValue().get(cache.dataKey()));assertNotEquals(before,redis.opsForValue().get(cache.generationKey()));assertTrue(cache.dataKey().startsWith(NAMESPACE+":stats:v1:admin:global"));
    }
    @Test void staleConcurrentRefillCannotPublishAfterCommittedInvalidation() throws Exception {
        account(true);cache.invalidate(new StatsChanged());var stale=source.load();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return stale;}).when(source).load();var pool=Executors.newSingleThreadExecutor();
        try{var future=pool.submit(cache::get);assertTrue(entered.await(5,TimeUnit.SECONDS));account(false);release.countDown();assertEquals(stale,future.get(10,TimeUnit.SECONDS));assertNull(redis.opsForValue().get(cache.dataKey()));reset(source);assertTrue(cache.get().users()>stale.users());}
        finally{release.countDown();pool.shutdownNow();}
    }
    @Test void exportsAcrossMultipleDatabasePagesAndRejectsOverCapBeforePublishingCsv() throws Exception {
        var admin=account(true);var user=account(false);for(int i=0;i<205;i++)audit.failure(user.id(),"p5_many",Map.of("index",i));
        var exported=csv(bytes(admin,"/api/logs/export?action=p5_many&userId="+user.id()));assertEquals(206,exported.size());noExportFiles();
        doReturn(10001L).when(logRepository).count(any());var tooMany=bytes(admin,"/api/logs/export?action=p5_many");assertEquals(400,tooMany.getStatusCode().value());assertTrue(tooMany.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));noExportFiles();reset(logRepository);
        doReturn(10001L).when(records).count(any());assertEquals(400,bytes(user,"/api/detect/records/export").getStatusCode().value());noExportFiles();
    }
    @Test void exportAuditFailureCleansPreparedCsvAndDoesNotPretendDownloadSucceeded() throws Exception {
        var admin=account(true);jdbc.execute("CREATE TRIGGER p5_export_audit_fail BEFORE INSERT ON operation_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P5 export audit failure'");
        try{assertTrue(bytes(admin,"/api/logs/export").getStatusCode().is5xxServerError());noExportFiles();}
        finally{jdbc.execute("DROP TRIGGER p5_export_audit_fail");}
    }
    @Test void recentStatisticsUseExactlyLatestHundredSuccessfulRecords() throws Exception {
        var admin=account(true);ready(admin);var result=data(single(admin,png(20,10),null));String template=result.path("record_id").asText();var ids=new ArrayList<String>();
        try {
            for(int i=0;i<101;i++) {
                String id=UUID.randomUUID().toString();ids.add(id);
                jdbc.update("""
                    INSERT INTO detection_records(id,user_id,model_id,original_storage_key,mask_storage_key,overlay_storage_key,channel_mask_keys,
                    original_width,original_height,total_pixels,defect_pixels,defect_count,inference_time_ms,model_name_snapshot,model_version_snapshot,inference_config_snapshot,created_at)
                    SELECT ?,user_id,model_id,original_storage_key,mask_storage_key,overlay_storage_key,channel_mask_keys,original_width,original_height,
                    total_pixels,defect_pixels,?,?,model_name_snapshot,model_version_snapshot,inference_config_snapshot,TIMESTAMPADD(SECOND,?,'2030-01-01 00:00:00') FROM detection_records WHERE id=?
                    """,id,i==0?99:2,i==0?9000:10,i,template);
            }
            var actual=source.load();assertEquals(100,actual.recent());assertEquals(10,actual.millis());assertEquals(2,actual.defects());
        }finally{for(String id:ids)jdbc.update("DELETE FROM detection_records WHERE id=?",id);cache.invalidate(new StatsChanged());}
    }
    @Test void failedRedisInvalidationNeverRollsBackCommittedDatabaseMutation() {
        var admin=account(true);cache.get();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("P5 invalidate unavailable")).when(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
        String id=auth.register(new AuthRequests.Register(UUID.randomUUID()+"@example.test","P5-test-password!","commit during Redis outage"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Integer.class,id));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("P5 read unavailable")).when(redis).opsForValue();assertEquals(source.load().users(),data(get(admin,"/api/admin/stats")).path("total_users").asLong());
    }
}
