package org.ssdd.task;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.util.LinkedMultiValueMap;
import org.ssdd.audit.AuditService;
import org.ssdd.auth.*;
import org.ssdd.detection.*;
import org.ssdd.inference.*;
import org.ssdd.model.*;
import org.ssdd.storage.DetectionStorage;
import org.ssdd.support.*;
import javax.sql.DataSource;
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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"ssdd.bootstrap-admin.enabled=false","ssdd.tasks.worker-enabled=false",
        "ssdd.tasks.capacity=2","ssdd.tasks.max-attempts=2","ssdd.tasks.progress-ttl=1s","ssdd.tasks.retry-delay=100ms",
        "ssdd.rate-limit.enabled=false","ssdd.detection.rate-limit-enabled=false","ssdd.auth.bcrypt-strength=10",
        "ssdd.auth.signing-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","ssdd.inference.device=cpu"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class TaskIntegrationTests {
    private static final Path ROOT=Path.of("target/p55-tasks-"+UUID.randomUUID()).toAbsolutePath();
    private static final String NAMESPACE="ssdd:p55-test:"+UUID.randomUUID();
    private static MySqlTestDatabase database;
    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;
    @Autowired ModelService models;
    @Autowired ModelRepository modelRepository;
    @Autowired DetectionRepository records;
    @Autowired TaskRepository tasks;
    @Autowired TaskService service;
    @Autowired TaskWorker worker;
    @Autowired TaskProgress progress;
    @Autowired TaskProperties config;
    @MockitoSpyBean DetectionService detection;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource dataSource;
    @MockitoSpyBean ModelRuntime runtime;
    @MockitoSpyBean InferenceSessionFactory sessions;
    @MockitoSpyBean DetectionStorage outputs;
    @MockitoSpyBean StringRedisTemplate redis;
    @MockitoSpyBean DetectionRateLimiter limiter;
    @MockitoSpyBean AuditService audit;
    static boolean mysqlAvailable(){return MySqlTestDatabase.available();}
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) throws Exception {
        database=MySqlTestDatabase.open();r.add("ssdd.database.url",database::url);r.add("ssdd.database.username",database::username);r.add("ssdd.database.password",database::password);
        r.add("ssdd.storage.root",ROOT::toString);r.add("ssdd.rate-limit.namespace",()->NAMESPACE);
    }
    @AfterEach void resetAndClean(){reset(runtime,sessions,outputs,redis,limiter,audit,detection);jdbc.update("UPDATE detection_tasks SET status='failed',error_code='TEST_CLEANUP' WHERE status IN ('queued','running','retry_wait')");service.cleanupTerminal();}
    @AfterAll static void cleanup(@Autowired TaskWorker worker,@Autowired ModelRuntime runtime,@Autowired StringRedisTemplate redis) throws Exception {
        var done=new CountDownLatch(1);worker.stop(done::countDown);assertTrue(done.await(5,TimeUnit.SECONDS));runtime.shutdown();
        try(var keys=redis.scan(ScanOptions.scanOptions().match(NAMESPACE+":*").count(100).build())){while(keys.hasNext())redis.delete(keys.next());}
        finally{if(database!=null)database.close();if(Files.exists(ROOT))try(var paths=Files.walk(ROOT)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    record Account(String id,String token){@Override public String toString(){return "Account[REDACTED]";}}
    private Account account(boolean admin){String email=UUID.randomUUID()+"@example.test",password="P55-test-password!";var input=new AuthRequests.Register(email,password,"Task tester");if(admin)auth.bootstrap(input);else auth.register(input);var result=auth.login(new AuthRequests.Login(email,password),"127.0.0.1","P55 tests");return new Account(result.user().id(),result.session().accessToken());}
    private String model(Account admin){return models.upload(admin.id(),new MockMultipartFile("model","task.onnx","application/octet-stream",OnnxFixtures.valid()),"Task model "+UUID.randomUUID(),"v2",null,"v2",4).get("id").toString();}
    private String ready(Account admin){String id=model(admin);models.activate(admin.id(),id);return id;}
    private HttpHeaders headers(Account actor){var h=new HttpHeaders();if(actor!=null)h.setBearerAuth(actor.token());return h;}
    private ResponseEntity<JsonNode> submit(Account actor,List<byte[]> images,List<String> names,String key){
        var h=headers(actor);h.setContentType(MediaType.MULTIPART_FORM_DATA);if(key!=null)h.set("Idempotency-Key",key);var body=new LinkedMultiValueMap<String,Object>();
        for(int i=0;i<images.size();i++){String name=names.get(i);body.add("images",new ByteArrayResource(images.get(i)){@Override public String getFilename(){return name;}});}
        return http.exchange("/api/detect/tasks",HttpMethod.POST,new HttpEntity<>(body,h),JsonNode.class);
    }
    private String submit(Account actor,int count,String key) throws Exception{return accepted(submit(actor,Collections.nCopies(count,png(32,16)),Collections.nCopies(count,"frame.png"),key)).path("task_id").asText();}
    private JsonNode accepted(ResponseEntity<JsonNode> response){assertEquals(202,response.getStatusCode().value(),()->String.valueOf(response.getBody()));assertTrue(response.getHeaders().getFirst("Location").startsWith("/api/detect/tasks/"));return response.getBody().path("data");}
    private ResponseEntity<JsonNode> get(Account actor,String id){return http.exchange("/api/detect/tasks/"+id,HttpMethod.GET,new HttpEntity<>(headers(actor)),JsonNode.class);}
    private JsonNode status(Account actor,String id){var response=get(actor,id);assertEquals(200,response.getStatusCode().value(),()->String.valueOf(response.getBody()));return response.getBody().path("data");}
    private long rows(){return jdbc.queryForObject("SELECT COUNT(*) FROM detection_records",Long.class);}
    private long countTasks(){return jdbc.queryForObject("SELECT COUNT(*) FROM detection_tasks",Long.class);}
    private long countFiles(String folder) throws Exception{Path path=ROOT.resolve(folder);if(!Files.exists(path))return 0;try(var paths=Files.walk(path)){return paths.filter(Files::isRegularFile).count();}}
    private void due(String id){jdbc.update("UPDATE detection_tasks SET next_attempt_at=UTC_TIMESTAMP(6) WHERE id=?",id);}
    private ModelRuntime.Lease fakeLease(String task){var frozen=json.convertValue(json.valueToTree(parseFrozen(task)),TaskService.Frozen.class);var lease=mock(ModelRuntime.Lease.class);when(lease.model()).thenReturn(frozen.model());when(lease.device()).thenReturn(frozen.device());doReturn(lease).when(runtime).acquireFrozen(any(),any(),any(),any());return lease;}
    private TaskService.Frozen parseFrozen(String task){try{return json.readValue(tasks.find(task).snapshot(),TaskService.Frozen.class);}catch(Exception ex){throw new RuntimeException(ex);}}
    private InferenceSession.RunResult negative(){float[] values=new float[4*1600*256];Arrays.fill(values,-1);return new InferenceSession.RunResult(values,1);}

    @Test void idleAndFutureRetryPollingNeverReservesSynchronousDetectionCapacity() throws Exception {
        clearInvocations(detection);worker.runOnce();verify(detection,never()).admitBackground();
        var admin=account(true);ready(admin);String id=submit(admin,1,null);
        jdbc.update("UPDATE detection_tasks SET status='retry_wait',next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 10 MINUTE) WHERE id=?",id);
        clearInvocations(detection);worker.runOnce();verify(detection,never()).admitBackground();
        assertEquals(0,tasks.find(id).attempts());assertEquals("retry_wait",tasks.find(id).status());
    }
    @Test void returns202ThenPublishesWholeBatchAndFreshAuthorizedResources() throws Exception {
        var admin=account(true);String model=ready(admin);var user=account(false);long before=rows();String id=submit(user,2,null);
        assertEquals("queued",status(user,id).path("status").asText());assertEquals(id,jdbc.queryForObject("SELECT target_id FROM operation_logs WHERE action='task_submit' AND JSON_UNQUOTE(JSON_EXTRACT(details,'$.task_id'))=?",String.class,id));assertEquals(before,rows());assertEquals(2,countFiles("task-inputs"));worker.runOnce();
        var result=status(user,id);assertEquals("succeeded",result.path("status").asText());assertEquals(1,result.path("attempts").asInt());assertEquals(model,result.path("model").path("id").asText());assertEquals(before+2,rows());assertEquals(0,countFiles("task-inputs"));
        for(var item:result.path("result").path("results")){assertEquals("frame.png",item.path("file_name").asText());assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM detection_class_stats WHERE record_id=?",Integer.class,item.path("record_id").asText()));assertEquals(200,http.getForEntity(item.path("original_image_url").asText(),byte[].class).getStatusCode().value());}
        worker.runOnce();assertEquals(before+2,rows());assertFalse(result.toString().contains(ROOT.toString()));assertFalse(result.has("input_manifest"));
        var exported=http.exchange("/api/logs/export?action=task_submit&userId="+user.id(),HttpMethod.GET,new HttpEntity<>(headers(user)),byte[].class);assertEquals(200,exported.getStatusCode().value());assertTrue(new String(exported.getBody(),java.nio.charset.StandardCharsets.UTF_8).contains("提交异步任务"));
    }
    @Test void enforcesOwnerAdminAndInputPrivacyForQueuedAndCompletedTasks() throws Exception {
        var admin=account(true);ready(admin);var owner=account(false);var other=account(false);String id=submit(owner,1,null);
        assertEquals(401,get(null,id).getStatusCode().value());assertEquals(404,get(other,id).getStatusCode().value());assertEquals(200,get(admin,id).getStatusCode().value());
        assertEquals(404,http.getForEntity("/uploads/task-inputs/"+id+"/00.image",byte[].class).getStatusCode().value());worker.runOnce();assertEquals(404,get(other,id).getStatusCode().value());assertEquals("succeeded",status(owner,id).path("status").asText());
    }
    @Test void rejectsBadAndOversizedBatchesBeforeModelLeaseAndCompensatesStagedInput() throws Exception {
        var admin=account(true);ready(admin);long before=countTasks();clearInvocations(runtime);
        assertEquals(400,submit(admin,List.of(png(20,10),new byte[]{1,2}),List.of("ok","bad"),null).getStatusCode().value());
        assertEquals(400,submit(admin,Collections.nCopies(21,png(20,10)),Collections.nCopies(21,"x"),null).getStatusCode().value());
        assertEquals(400,submit(admin,List.of(),List.of(),null).getStatusCode().value());verify(runtime,never()).acquire();assertEquals(before,countTasks());assertEquals(0,countFiles("task-inputs"));
    }
    @Test void replayReturnsOriginalTaskWhileOrderOrNamesConflictAndUsersAreIsolated() throws Exception {
        var admin=account(true);ready(admin);String key=UUID.randomUUID().toString();byte[] a=png(20,10),b=png(21,10);long before=countTasks();
        String id=accepted(submit(admin,List.of(a,b),List.of("a","b"),key)).path("task_id").asText();
        assertEquals(id,accepted(submit(admin,List.of(a,b),List.of("a","b"),key)).path("task_id").asText());assertEquals(409,submit(admin,List.of(b,a),List.of("b","a"),key).getStatusCode().value());assertEquals(409,submit(admin,List.of(a,b),List.of("renamed","b"),key).getStatusCode().value());
        worker.runOnce();doThrow(ModelErrors.unavailable("paused")).when(runtime).acquire();assertEquals(id,accepted(submit(admin,List.of(a,b),List.of("a","b"),key)).path("task_id").asText());reset(runtime);
        var other=account(false);assertNotEquals(id,accepted(submit(other,List.of(a,b),List.of("a","b"),key)).path("task_id").asText());assertEquals(before+2,countTasks());
    }
    @Test void concurrentAdmissionCannotExceedDurableCapacity() throws Exception {
        var admin=account(true);ready(admin);long before=countTasks();byte[] image=png(20,10);var pool=Executors.newFixedThreadPool(6);
        try{var jobs=new ArrayList<Callable<Integer>>();for(int i=0;i<6;i++)jobs.add(()->submit(admin,List.of(image),List.of("x"),UUID.randomUUID().toString()).getStatusCode().value());int accepted=0;for(var result:pool.invokeAll(jobs)){int code=result.get();if(code==202)accepted++;else assertEquals(503,code);}assertEquals(2,accepted);assertEquals(before+2,countTasks());assertEquals(2,tasks.pendingCount());assertEquals(2,countFiles("task-inputs"));}
        finally{pool.shutdownNow();}
    }
    @Test void queuedTaskKeepsModelNameAndDeviceAcrossRenameActivationAndSoftDelete() throws Exception {
        var admin=account(true);String old=ready(admin);String id=submit(admin,1,null);String name=status(admin,id).path("model").path("name").asText();
        models.update(admin.id(),old,Map.of("name",json.getNodeFactory().textNode("changed after submission")));String next=ready(admin);models.delete(admin.id(),old);assertTrue(Files.exists(ROOT.resolve("models/"+old+".onnx")));
        worker.runOnce();var result=status(admin,id);assertEquals("succeeded",result.path("status").asText());assertEquals(name,result.path("result").path("model").path("name").asText());assertEquals(old,result.path("result").path("model").path("id").asText());assertEquals(next,runtime.status().get("active_model_id"));assertEquals("cpu",result.path("actual_device").asText());
    }
    @Test void progressCacheExpiresAndRedisOutageDoesNotLoseTaskStateOrExecution() throws Exception {
        var admin=account(true);ready(admin);String id=submit(admin,1,null);redis.delete(progress.key(id));assertEquals("mysql",status(admin,id).path("progress_source").asText());assertEquals("redis",status(admin,id).path("progress_source").asText());Thread.sleep(1100);assertEquals("mysql",status(admin,id).path("progress_source").asText());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("cache offline")).when(redis).opsForValue();assertEquals("mysql",status(admin,id).path("progress_source").asText());worker.runOnce();assertEquals("succeeded",status(admin,id).path("status").asText());
    }
    @Test void retryIsBoundedAndDoesNotPublishFailedAttemptArtifacts() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),files=countFiles("results");String id=submit(admin,1,null);var lease=fakeLease(id);doThrow(ModelErrors.unavailable("injected")).when(lease).runTimed(any());
        worker.runOnce();assertEquals("retry_wait",tasks.find(id).status());assertEquals(1,tasks.find(id).attempts());assertEquals(before,rows());due(id);worker.runOnce();assertEquals("failed",tasks.find(id).status());assertEquals(2,tasks.find(id).attempts());worker.runOnce();verify(lease,times(2)).runTimed(any());assertEquals(files,countFiles("results"));assertEquals(0,countFiles("task-inputs"));
    }
    @Test void secondImageFailureThenRetryPublishesExactlyOneCompleteBatch() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),files=countFiles("results");String id=submit(admin,2,null);var lease=fakeLease(id);var calls=new AtomicInteger();when(lease.runTimed(any())).thenAnswer(call->{if(calls.incrementAndGet()==2)throw ModelErrors.unavailable("second image");return negative();});
        worker.runOnce();assertEquals("retry_wait",tasks.find(id).status());assertEquals(before,rows());assertEquals(files,countFiles("results"));due(id);worker.runOnce();assertEquals("succeeded",tasks.find(id).status());assertEquals(before+2,rows());assertEquals(2,records.batchRows(tasks.find(id).batchId()).size());worker.runOnce();assertEquals(before+2,rows());
    }
    @Test void recoveryFencesOldAttemptAndCleansItsUnpublishedOutputBeforeRetry() throws Exception {
        var admin=account(true);ready(admin);long before=rows();String id=submit(admin,1,null);var tx=new TransactionTemplate(manager);var old=tx.execute(status->tasks.claim());String orphan=UUID.randomUUID().toString();outputs.save(orphan,Map.of("original",new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB)));tasks.checkpoint(id,old.token(),0,json.writeValueAsString(List.of(orphan)));old=tasks.find(id);
        service.recoverInterrupted();assertEquals("queued",tasks.find(id).status());assertFalse(Files.exists(ROOT.resolve("results/"+orphan)));var stale=old;assertThrows(TaskFault.class,()->tx.executeWithoutResult(status->tasks.requireAttempt(id,stale.token())));
        service.execute(stale,()->true);assertEquals("queued",tasks.find(id).status());worker.runOnce();assertEquals("succeeded",tasks.find(id).status());assertEquals(2,tasks.find(id).attempts());assertEquals(before+1,rows());service.execute(stale,()->true);assertEquals(before+1,rows());
    }
    @Test void anotherWorkerCannotRecoverOrExecuteWhileOwnershipLockIsHeld() throws Exception {
        var admin=account(true);ready(admin);worker.runOnce();String id=submit(admin,1,null);var second=new TaskWorker(config,tasks,service,detection,dataSource,manager);
        try{second.runOnce();assertEquals("queued",tasks.find(id).status());assertEquals(0,tasks.find(id).attempts());worker.runOnce();assertEquals("succeeded",tasks.find(id).status());}
        finally{var done=new CountDownLatch(1);second.stop(done::countDown);assertTrue(done.await(5,TimeUnit.SECONDS));}
    }
    @Test void missingInputAndDisabledOwnerFailWithoutResultPublication() throws Exception {
        var admin=account(true);ready(admin);long before=rows();String missing=submit(admin,1,null);Files.delete(ROOT.resolve("task-inputs/"+missing+"/00.image"));worker.runOnce();assertEquals("failed",tasks.find(missing).status());assertEquals("INPUT_MISSING",tasks.find(missing).error());assertEquals(1,tasks.find(missing).attempts());
        var user=account(false);String disabled=submit(user,1,null);jdbc.update("UPDATE users SET status='disabled' WHERE id=?",user.id());worker.runOnce();assertEquals("failed",tasks.find(disabled).status());assertEquals("USER_DISABLED",tasks.find(disabled).error());assertEquals(before,rows());
    }
    @Test void expiryAndAttemptTimeoutHaveBoundedRetryAndReleaseAdmission() throws Exception {
        var admin=account(true);ready(admin);long before=rows();String expired=submit(admin,1,null);jdbc.update("UPDATE detection_tasks SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",expired);worker.runOnce();assertEquals("TASK_EXPIRED",tasks.find(expired).error());assertEquals(0,tasks.find(expired).attempts());
        String slow=submit(admin,1,null);var frozen=parseFrozen(slow);jdbc.update("UPDATE detection_tasks SET model_snapshot=? WHERE id=?",json.writeValueAsString(new TaskService.Frozen(frozen.model(),frozen.device(),frozen.inference(),Duration.ofSeconds(1))),slow);var lease=fakeLease(slow);when(lease.runTimed(any())).thenAnswer(call->{Thread.sleep(1100);return negative();});worker.runOnce();assertEquals("retry_wait",tasks.find(slow).status());assertEquals("ATTEMPT_TIMEOUT",tasks.find(slow).error());assertEquals(before,rows());doReturn(negative()).when(lease).runTimed(any());due(slow);worker.runOnce();assertEquals("succeeded",tasks.find(slow).status());
    }
    @Test void publishAuditFailureRollsBackEntireAttemptThenCanRetry() throws Exception {
        var admin=account(true);ready(admin);long before=rows(),files=countFiles("results");String id=submit(admin,2,null);jdbc.execute("CREATE TRIGGER p55_fail BEFORE INSERT ON operation_logs FOR EACH ROW BEGIN IF NEW.action='batch_detect' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='publish audit failure'; END IF; END");
        try{worker.runOnce();assertEquals("retry_wait",tasks.find(id).status());assertEquals(before,rows());assertEquals(files,countFiles("results"));}finally{jdbc.execute("DROP TRIGGER p55_fail");}
        due(id);worker.runOnce();assertEquals("succeeded",tasks.find(id).status());assertEquals(before+2,rows());
    }
    @Test void submissionRollbackRemovesPrivateInputsAndRateRejectionNeverStagesFiles() throws Exception {
        var admin=account(true);ready(admin);long before=countTasks();jdbc.execute("CREATE TRIGGER p55_submit_fail BEFORE INSERT ON detection_tasks FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='submission failure'");
        try{assertTrue(submit(admin,List.of(png(20,10)),List.of("x"),null).getStatusCode().is5xxServerError());}finally{jdbc.execute("DROP TRIGGER p55_submit_fail");}
        assertEquals(before,countTasks());assertEquals(0,countFiles("task-inputs"));doThrow(new org.ssdd.ratelimit.RateLimitException(1000)).when(limiter).check(admin.id());assertEquals(429,submit(admin,List.of(png(20,10)),List.of("x"),null).getStatusCode().value());assertEquals(0,countFiles("task-inputs"));
    }
    @Test void lostSuccessCommitAcknowledgementRetainsPublishedFilesAndPreventsDuplicates() throws Exception {
        var admin=account(true);ready(admin);long before=rows();String id=submit(admin,2,null);
        doAnswer(call->{call.callRealMethod();if(call.getArgument(1).equals("batch_detect"))TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){throw new IllegalStateException("ack lost");}});return null;}).when(audit).success(any(),any(),any());
        worker.runOnce();assertEquals("succeeded",tasks.find(id).status());assertEquals(before+2,rows());for(var row:records.batchRows(tasks.find(id).batchId()))assertTrue(Files.exists(ROOT.resolve(row.originalKey())));worker.runOnce();assertEquals(before+2,rows());
    }
    @Test void lostSubmissionAcknowledgementCanReplayDurableTask() throws Exception {
        var admin=account(true);ready(admin);long before=countTasks();String key=UUID.randomUUID().toString();byte[] image=png(20,10);
        doAnswer(call->{call.callRealMethod();if(call.getArgument(1).equals("task_submit"))TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){throw new IllegalStateException("ack lost");}});return null;}).when(audit).success(any(),any(),any());
        assertTrue(submit(admin,List.of(image),List.of("x"),key).getStatusCode().is5xxServerError());assertEquals(before+1,countTasks());reset(audit);String id=accepted(submit(admin,List.of(image),List.of("x"),key)).path("task_id").asText();worker.runOnce();assertEquals("succeeded",tasks.find(id).status());assertEquals(before+1,countTasks());
    }
    @Test void failedStateAuditRecoversWithoutUnboundedExecution() throws Exception {
        var admin=account(true);ready(admin);long before=rows();String id=submit(admin,1,null);jdbc.execute("CREATE TRIGGER p55_all_audit_fail BEFORE INSERT ON operation_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='all audit unavailable'");
        try{worker.runOnce();worker.runOnce();worker.runOnce();assertEquals("failed",tasks.find(id).status());assertEquals(2,tasks.find(id).attempts());assertEquals(before,rows());}
        finally{jdbc.execute("DROP TRIGGER p55_all_audit_fail");}
    }
}
