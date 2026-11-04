package org.ssdd.detection;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.ssdd.auth.*;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.inference.*;
import org.ssdd.model.*;
import org.ssdd.ratelimit.*;
import org.ssdd.storage.*;
import org.ssdd.support.*;
import javax.imageio.ImageIO;
import java.io.*;
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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "ssdd.bootstrap-admin.enabled=false","ssdd.rate-limit.enabled=false","ssdd.detection.rate-limit-enabled=true",
        "ssdd.detection.requests-per-window=1000","ssdd.auth.bcrypt-strength=10",
        "ssdd.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","ssdd.inference.device=cpu"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class DetectionIntegrationTests {
    private static final Path ROOT=Path.of("target/p4-integration-"+UUID.randomUUID()).toAbsolutePath();
    private static final String NAMESPACE="ssdd:p4-test:"+UUID.randomUUID();
    private static MySqlTestDatabase database;
    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;
    @Autowired ModelService models;
    @Autowired ModelRepository modelRepository;
    @Autowired DetectionService service;
    @Autowired DetectionRepository records;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired RateLimitProperties rate;
    @Autowired ObjectMapper json;
    @Autowired DetectionProperties detectionConfig;
    @MockitoSpyBean DetectionRateLimiter limiter;
    @MockitoSpyBean ModelRuntime runtime;
    @MockitoSpyBean InferenceSessionFactory factory;
    @MockitoSpyBean DetectionStorage storage;
    static boolean mysqlAvailable(){return MySqlTestDatabase.available();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
        database=MySqlTestDatabase.open();r.add("ssdd.database.url",database::url);r.add("ssdd.database.username",database::username);r.add("ssdd.database.password",database::password);
        r.add("ssdd.storage.root",ROOT::toString);r.add("ssdd.rate-limit.namespace",()->NAMESPACE);
    }
    @AfterEach void resetSpies(){reset(factory,runtime,storage,limiter);}
    @AfterAll static void cleanup(@Autowired ModelRuntime runtime,@Autowired StringRedisTemplate redis) throws Exception {
        runtime.shutdown();
        try(var keys=redis.scan(ScanOptions.scanOptions().match(NAMESPACE+":*").count(100).build())){while(keys.hasNext())redis.delete(keys.next());}
        finally {if(database!=null)database.close();if(Files.exists(ROOT))try(var paths=Files.walk(ROOT)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    record Account(String id,String token,String role) {SessionPrincipal principal(){return new SessionPrincipal(id,"test-session",role);}@Override public String toString(){return "Account[REDACTED]";}}
    private Account account(boolean admin) {
        String email=UUID.randomUUID()+"@example.test",password="P4-test-password!";var request=new AuthRequests.Register(email,password,"P4 tester");
        if(admin)auth.bootstrap(request);else auth.register(request);
        var result=auth.login(new AuthRequests.Login(email,password),"127.0.0.1","P4 test");return new Account(result.user().id(),result.session().accessToken(),admin?"admin":"inspector");
    }
    private String model(Account admin) {
        var row=models.upload(admin.id(),new org.springframework.mock.web.MockMultipartFile("model","v2.onnx","application/octet-stream",OnnxFixtures.valid()),"P4 "+UUID.randomUUID(),"2",null,"v2",4);
        return row.get("id").toString();
    }
    private String ready(Account admin){String id=model(admin);models.activate(admin.id(),id);return id;}
    private ResponseEntity<JsonNode> detect(Account actor,byte[] bytes,String key) {
        var headers=new HttpHeaders();if(actor!=null)headers.setBearerAuth(actor.token());headers.setContentType(MediaType.MULTIPART_FORM_DATA);if(key!=null)headers.set("Idempotency-Key",key);
        var body=new LinkedMultiValueMap<String,Object>();body.add("image",new ByteArrayResource(bytes){@Override public String getFilename(){return "../image.fake";}});
        return http.exchange("/api/detect",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private ResponseEntity<JsonNode> get(Account actor,String path) {var headers=new HttpHeaders();if(actor!=null)headers.setBearerAuth(actor.token());return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);}
    private static JsonNode data(ResponseEntity<JsonNode> response){assertEquals(200,response.getStatusCode().value(),()->String.valueOf(response.getBody()));return response.getBody().path("data");}
    private long rows(){return jdbc.queryForObject("SELECT COUNT(*) FROM detection_records",Long.class);}
    private long files() throws IOException {Path dir=ROOT.resolve("results");if(!Files.exists(dir))return 0;try(var paths=Files.walk(dir)){return paths.filter(Files::isRegularFile).count();}}

    @Test void publishesRealInferenceWithFourLosslessMasksSnapshotAndSignedAssets() throws Exception {
        var admin=account(true);String model=ready(admin);var inspector=account(false);long before=rows();
        assertEquals(401,detect(null,png(80,40),null).getStatusCode().value());
        JsonNode result=data(detect(inspector,png(80,40),null));String id=result.path("record_id").asText();
        assertEquals(before+1,rows());assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM detection_class_stats WHERE record_id=?",Integer.class,id));
        assertEquals(model,result.path("model").path("id").asText());assertEquals(3200,result.path("defect_info").path("total_pixels").asLong());
        assertEquals("cpu",result.path("inference_config_snapshot").path("actual_device").asText());assertTrue(result.path("inference_time_ms").asDouble()>=0);
        assertEquals(4,result.path("channel_mask_urls").size());
        var stored=records.find(id);assertFalse(stored.originalKey().contains("?"));assertFalse(result.toString().contains(ROOT.toString()));
        for(String field:List.of("original_image_url","mask_image_url","overlay_image_url")) {
            var image=http.getForEntity(result.path(field).asText(),byte[].class);assertEquals(200,image.getStatusCode().value());assertEquals(MediaType.IMAGE_PNG,image.getHeaders().getContentType());
            assertEquals("no-store",image.getHeaders().getCacheControl());var decoded=ImageIO.read(new ByteArrayInputStream(image.getBody()));assertEquals(80,decoded.getWidth());assertEquals(40,decoded.getHeight());
        }
        for(var url:result.path("channel_mask_urls"))assertEquals(200,http.getForEntity(url.asText(),byte[].class).getStatusCode().value());
    }
    @Test void scopesHistoryAndDetailToOwnerButLetsAdminFilterAndKeepsModelSnapshots() throws Exception {
        var admin=account(true);String id=ready(admin);var a=account(false);var b=account(false);
        var first=data(detect(a,png(32,16),null));var second=data(detect(b,png(32,16),null));String record=first.path("record_id").asText();
        assertEquals(401,get(null,"/api/detect/records").getStatusCode().value());
        assertEquals(404,get(b,"/api/detect/records/"+record).getStatusCode().value());
        var owned=data(get(a,"/api/detect/records?userId="+b.id()+"&search=anything&pageSize=1"));assertEquals(1,owned.path("total").asInt());assertEquals(record,owned.path("items").get(0).path("id").asText());
        assertEquals(1,data(get(admin,"/api/detect/records?userId="+b.id())).path("total").asInt());
        var all=data(get(admin,"/api/detect/records?all=true&page=2&pageSize=1"));
        assertEquals(all.path("total").asInt(),all.path("items").size());assertEquals(all.path("total").asInt(),all.path("pageSize").asInt());assertEquals(1,all.path("page").asInt());assertEquals(1,all.path("totalPages").asInt());
        assertEquals(0,data(get(admin,"/api/detect/records?search=%25_")).path("total").asInt());
        assertEquals(400,get(a,"/api/detect/records?page=0").getStatusCode().value());assertEquals(400,get(admin,"/api/detect/records?userId=bad").getStatusCode().value());
        String oldName=first.path("model").path("name").asText();models.update(admin.id(),id,Map.of("name",json.getNodeFactory().textNode("Renamed")));
        ready(admin);models.delete(admin.id(),id);
        var detail=data(get(admin,"/api/detect/records/"+record));assertEquals(record,detail.path("record_id").asText());assertEquals(oldName,detail.path("models").path("name").asText());assertEquals(a.id(),detail.path("user_id").asText());
        assertTrue(Files.exists(ROOT.resolve(records.find(record).originalKey())));assertTrue(second.has("record_id"));
    }
    @Test void rejectsUnsignedExpiredTamperedAndSwappedResourceCapabilities() throws Exception {
        var admin=account(true);ready(admin);var result=data(detect(admin,png(20,10),null));String url=result.path("mask_image_url").asText();
        for(String invalid:List.of(url.replace("mask.png","overlay.png"),url.replaceFirst("signature=.","signature=Xx"),url.replaceFirst("expires=\\d+","expires=1"),url.replaceFirst("expires=\\d+","expires=9999999999"),url.replace(result.path("record_id").asText(),UUID.randomUUID().toString())))
            assertEquals(404,http.getForEntity(invalid,byte[].class).getStatusCode().value());
        String bare=url.substring(0,url.indexOf('?'));assertEquals(400,http.getForEntity(bare,byte[].class).getStatusCode().value());
        var headers=new HttpHeaders();headers.setBearerAuth("expired-token");assertEquals(200,http.exchange(url,HttpMethod.GET,new HttpEntity<>(headers),byte[].class).getStatusCode().value());
        assertEquals(400,http.getForEntity("/uploads/"+records.find(result.path("record_id").asText()).originalKey(),byte[].class).getStatusCode().value());
    }
    @Test void replaysCommittedIdempotencyKeyAndRejectsChangedBytesWithoutDuplicateRows() throws Exception {
        var admin=account(true);ready(admin);long before=rows();String key=UUID.randomUUID().toString();byte[] bytes=png(31,17);
        var first=data(detect(admin,bytes,key));var replay=data(detect(admin,bytes,key));assertEquals(first.path("record_id"),replay.path("record_id"));assertEquals(before+1,rows());
        assertEquals(409,detect(admin,png(32,17),key).getStatusCode().value());
        assertEquals(400,detect(admin,bytes,"bad key").getStatusCode().value());
        var other=account(false);assertNotEquals(first.path("record_id"),data(detect(other,bytes,key)).path("record_id"));
    }
    @Test void rejectsBadUploadsBeforeInferenceAndLeavesNoPublishedRowsOrFiles() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),files=files();
        for(byte[] invalid:List.of(new byte[]{1,2,3},new byte[0],new byte[10485761],png(8193,1)))assertEquals(400,detect(admin,invalid,null).getStatusCode().value());
        assertEquals(before,rows());assertEquals(files,files());
    }
    @Test void compensatesFilesAndRowsWhenClassInsertOrAuditFails() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),files=files();
        for(String table:List.of("detection_class_stats","operation_logs")) {
            jdbc.execute("CREATE TRIGGER p4_fail BEFORE INSERT ON "+table+" FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P4 injected rollback'");
            try {assertTrue(detect(admin,png(50,25),null).getStatusCode().is5xxServerError());}
            finally {jdbc.execute("DROP TRIGGER p4_fail");}
            assertEquals(before,rows());assertEquals(files,files());
        }
    }
    @Test void nativeFailureTimeoutAndStorageFailureReleaseCapacityWithoutPublishing() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),files=files();
        var active=modelRepository.find(runtime.status().get("active_model_id").toString());
        var lease=mock(ModelRuntime.Lease.class);when(lease.model()).thenReturn(active);
        when(lease.device()).thenReturn(new InferenceSession.DeviceInfo("cpu","cpu",null,"test"));
        for(int status:List.of(503,504)) {
            doReturn(lease).when(runtime).acquire();doThrow(new BusinessException(HttpStatus.valueOf(status),"Injected native failure")).when(lease).runTimed(any());
            assertEquals(status,detect(admin,png(50,25),null).getStatusCode().value());verify(lease,atLeastOnce()).close();
        }
        reset(runtime);doThrow(DetectionErrors.busy()).when(storage).save(any(),any());assertEquals(503,detect(admin,png(50,25),null).getStatusCode().value());reset(storage);
        assertEquals(before,rows());assertEquals(files,files());data(detect(admin,png(50,25),null));
    }
    @Test void holdsOldLeaseUntilCommitAcrossSwitchAndDeleteAndRejectsConcurrentWork() throws Exception {
        var admin=account(true);String old=model(admin),replacement=model(admin);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var closed=new AtomicBoolean();
        var fake=new InferenceSession(){public DeviceInfo deviceInfo(){return new DeviceInfo("auto","cpu","CUDA_INITIALIZATION_FAILED","test");}
            public float[] run(float[] input){entered.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}catch(InterruptedException ex){throw new RuntimeException(ex);}float[] values=new float[4*1600*256];Arrays.fill(values,-1);return values;}
            public void close(){closed.set(true);}};
        doReturn(fake).when(factory).openAndWarm(any());models.activate(admin.id(),old);reset(factory);
        var pool=Executors.newSingleThreadExecutor();String key=UUID.randomUUID().toString();
        try {
            var future=pool.submit(()->detect(admin,png(64,32),key));assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertEquals(409,detect(admin,png(64,32),key).getStatusCode().value());assertEquals(503,detect(admin,png(64,32),null).getStatusCode().value());
            models.activate(admin.id(),replacement);models.delete(admin.id(),old);assertFalse(closed.get());
            release.countDown();var result=data(future.get(20,TimeUnit.SECONDS));assertEquals(old,result.path("model").path("id").asText());assertEquals("CUDA_INITIALIZATION_FAILED",result.path("inference_config_snapshot").path("fallback_reason").asText());
            assertTrue(closed.get());assertTrue(Files.exists(ROOT.resolve("models/"+old+".onnx")));assertNotNull(records.find(result.path("record_id").asText()));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void rechecksDisabledUserAtPublishAndPreservesOnlyFailureAudit() throws Exception {
        var admin=account(true);ready(admin);var user=account(false);long before=rows(),files=files();
        var lease=runtime.acquire();var proxy=mock(ModelRuntime.Lease.class);when(proxy.model()).thenReturn(lease.model());when(proxy.device()).thenReturn(lease.device());
        when(proxy.runTimed(any())).thenAnswer(call->{jdbc.update("UPDATE users SET status='disabled' WHERE id=?",user.id());float[] output=new float[4*1600*256];Arrays.fill(output,-1);return new InferenceSession.RunResult(output,1);});
        doReturn(proxy).when(runtime).acquire();try {assertEquals(403,detect(user,png(20,10),null).getStatusCode().value());}finally{lease.close();}
        assertEquals(before,rows());assertEquals(files,files());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM operation_logs WHERE user_id=? AND action='detect' AND status='failure'",Integer.class,user.id()));
    }
    @Test void redisQuotaIsAtomicExpiresSeparatesUsersAndFailsClosedOnOutage() throws Exception {
        var limits=new DetectionProperties(10485760,8000000,8192,true,3,Duration.ofSeconds(1),Duration.ofMinutes(15),Duration.ofSeconds(30));
        var limiter=new DetectionRateLimiter(redis,limits,rate,new MockEnvironment());String user=UUID.randomUUID().toString();
        var pool=Executors.newFixedThreadPool(8);try {
            var jobs=new ArrayList<Callable<Integer>>();for(int i=0;i<12;i++)jobs.add(()->{try{limiter.check(user);return 200;}catch(BusinessException ex){return ex.getStatus().value();}});
            var results=pool.invokeAll(jobs);int allowed=0;for(var result:results){int status=result.get();if(status==200)allowed++;else assertEquals(429,status);}assertEquals(3,allowed);
        }finally{pool.shutdownNow();}
        assertTrue(redis.getExpire(limiter.key(user),TimeUnit.MILLISECONDS)>0);limiter.check(UUID.randomUUID().toString());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(Boolean.TRUE.equals(redis.hasKey(limiter.key(user)))&&System.nanoTime()<deadline)Thread.sleep(50);limiter.check(user);
        var broken=mock(StringRedisTemplate.class);doThrow(new org.springframework.dao.DataAccessResourceFailureException("injected")).when(broken).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
        var unavailable=new DetectionRateLimiter(broken,limits,rate,new MockEnvironment());assertEquals(503,assertThrows(BusinessException.class,()->unavailable.check(user)).getStatus().value());
    }
    @Test void httpQuotaReturnsRetryAfterAndRedisFailureNeverStartsInference() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),files=files();
        redis.opsForValue().set(limiter.key(admin.id()),"1000",Duration.ofSeconds(5));
        var response=detect(admin,png(20,10),null);assertEquals(429,response.getStatusCode().value());assertTrue(Long.parseLong(response.getHeaders().getFirst("Retry-After"))>0);
        redis.delete(limiter.key(admin.id()));clearInvocations(runtime);
        doThrow(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"限流服务暂不可用")).when(limiter).check(admin.id());
        assertEquals(503,detect(admin,png(20,10),null).getStatusCode().value());verify(runtime,never()).acquire();assertEquals(before,rows());assertEquals(files,files());
    }
    @Test void totalBudgetExpiryAfterNativeReturnClosesLeaseAndDoesNotPublish() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),files=files();
        var active=modelRepository.find(runtime.status().get("active_model_id").toString());var lease=mock(ModelRuntime.Lease.class);
        when(lease.model()).thenReturn(active);when(lease.device()).thenReturn(new InferenceSession.DeviceInfo("cpu","cpu",null,"test"));
        when(lease.runTimed(any())).thenAnswer(call->{Thread.sleep(1100);return new InferenceSession.RunResult(new float[4*1600*256],1100);});doReturn(lease).when(runtime).acquire();
        var shortBudget=new DetectionProperties(10485760,8000000,8192,true,1000,Duration.ofMinutes(1),Duration.ofMinutes(15),Duration.ofSeconds(1));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"config",shortBudget);
        try {assertEquals(504,detect(admin,png(20,10),null).getStatusCode().value());verify(lease).close();assertEquals(before,rows());assertEquals(files,files());}
        finally {org.springframework.test.util.ReflectionTestUtils.setField(service,"config",detectionConfig);}
        reset(runtime);data(detect(admin,png(20,10),null));
    }
}
