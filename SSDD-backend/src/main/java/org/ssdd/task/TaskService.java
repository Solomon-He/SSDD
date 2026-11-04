package org.ssdd.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.audit.AuditService;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.detection.*;
import org.ssdd.inference.*;
import org.ssdd.model.*;
import org.ssdd.storage.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

@Service
public class TaskService {
    public record Frozen(ModelRepository.ModelRow model,InferenceSession.DeviceInfo device,InferenceProperties inference,Duration attemptTimeout) {}
    private final TaskRepository tasks;
    private final TaskStorage inputs;
    private final TaskProgress progress;
    private final TaskProperties config;
    private final BatchProperties batches;
    private final InferenceProperties inference;
    private final DetectionImageDecoder decoder;
    private final DetectionRateLimiter limiter;
    private final DetectionRepository records;
    private final DetectionService detection;
    private final DetectionStorage results;
    private final ModelRuntime runtime;
    private final ModelStorage models;
    private final ModelService modelService;
    private final OnnxModelGuard guard;
    private final InferenceSessionFactory sessions;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Validator validator;
    private final TransactionTemplate tx;
    private final Set<String> submitting=ConcurrentHashMap.newKeySet();
    private final Semaphore uploads=new Semaphore(2);
    public TaskService(TaskRepository tasks,TaskStorage inputs,TaskProgress progress,TaskProperties config,BatchProperties batches,
            InferenceProperties inference,DetectionImageDecoder decoder,DetectionRateLimiter limiter,DetectionRepository records,
            DetectionService detection,DetectionStorage results,ModelRuntime runtime,ModelStorage models,ModelService modelService,
            OnnxModelGuard guard,InferenceSessionFactory sessions,AuditService audit,ObjectMapper json,Validator validator,PlatformTransactionManager manager){
        this.tasks=tasks;this.inputs=inputs;this.progress=progress;this.config=config;this.batches=batches;this.inference=inference;this.decoder=decoder;this.limiter=limiter;
        this.records=records;this.detection=detection;this.results=results;this.runtime=runtime;this.models=models;this.modelService=modelService;this.guard=guard;this.sessions=sessions;
        this.audit=audit;this.json=json;this.validator=validator;tx=new TransactionTemplate(manager);
    }
    public Map<String,Object> submit(SessionPrincipal actor,List<MultipartFile> images,String key){
        if(images==null||images.isEmpty()||images.size()>batches.maxImages())throw DetectionErrors.bad("任务图片数量必须在 1～"+batches.maxImages()+" 之间");
        if(key!=null&&!key.matches("[A-Za-z0-9._:-]{1,128}"))throw DetectionErrors.bad("无效的 Idempotency-Key");
        limiter.check(actor.userId());String reservation=key==null?null:actor.userId()+":"+key;
        if(reservation!=null&&!submitting.add(reservation))throw DetectionErrors.conflict();
        boolean admitted=false;String id=UUID.randomUUID().toString();
        try{
            if(!uploads.tryAcquire())throw DetectionErrors.busy();admitted=true;
            var manifest=new ArrayList<TaskStorage.Input>();
            for(int i=0;i<images.size();i++){
                var file=images.get(i);var upload=decoder.read(file);decoder.decode(upload.bytes());
                manifest.add(inputs.store(id,i,DetectionService.filename(file.getOriginalFilename(),i),upload));
            }
            String digest=digest(manifest);var existing=tasks.byKey(actor.userId(),key);
            if(existing!=null){sameDigest(existing,digest);inputs.discard(id);return view(existing);}
            try(var lease=runtime.acquire()){
                var frozen=new Frozen(lease.model(),lease.device(),inference,config.attemptTimeout());
                var accepted=tx.execute(status->{
                    tasks.lockControl();records.requireActiveUser(actor);var previous=tasks.byKey(actor.userId(),key);
                    if(previous!=null){sameDigest(previous,digest);return previous;}
                    tasks.capacity(config.capacity());
                    tasks.insert(id,actor.userId(),frozen.model().id(),key,digest,encode(frozen),encode(manifest),images.size(),config.maxAttempts(),Instant.now().plus(config.maxAge()));
                    audit.success(actor.userId(),"task_submit",Map.of("task_id",id,"model_id",frozen.model().id(),"image_count",images.size(),"actual_device",frozen.device().actual()));return tasks.find(id);
                });
                if(!accepted.id().equals(id))inputs.discard(id);
                progress.store(accepted);return view(accepted);
            }
        }catch(RuntimeException failure){
            try{if(tasks.find(id)==null)inputs.discard(id);}catch(RuntimeException uncertain){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Task submission outcome uncertain; retaining private inputs; task={}",id);}
            if(failure instanceof TaskFault)throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"异步任务输入暂不可用");
            throw failure;
        }finally{if(admitted)uploads.release();if(reservation!=null)submitting.remove(reservation);}
    }
    public Map<String,Object> detail(SessionPrincipal actor,String id){
        var row=tasks.find(id);if(row==null||(!actor.role().equals("admin")&&!row.userId().equals(actor.userId())))throw DetectionErrors.missing();return view(row);
    }
    private Map<String,Object> view(TaskRepository.Row row){
        var data=new LinkedHashMap<String,Object>();data.put("task_id",row.id());data.put("status",row.status());data.put("image_count",row.imageCount());data.put("completed_images",row.completed());
        data.put("progress_percent",100.0*row.completed()/row.imageCount());data.put("attempts",row.attempts());data.put("max_attempts",row.maxAttempts());
        var snapshot=tree(row.snapshot());var model=snapshot.path("model");data.put("model",Map.of("id",row.modelId(),"name",model.path("name").asText(),"version",model.path("version").asText()));
        data.put("requested_device",snapshot.path("device").path("requested").asText());data.put("actual_device",snapshot.path("device").path("actual").asText());
        data.put("fallback_reason",snapshot.path("device").path("fallbackReason"));data.put("error_code",row.error());
        data.put("created_at",row.created());data.put("updated_at",row.updated());data.put("next_attempt_at",row.nextAttempt());data.put("expires_at",row.expires());
        data.put("progress_source",progress.source(row));data.put("result",null);
        if(row.status().equals("succeeded")){
            var batch=records.batchRows(row.batchId());if(batch.size()!=row.imageCount())throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"任务结果暂不可用");
            var batchSnapshot=json.createObjectNode();batchSnapshot.set("model",json.valueToTree(data.get("model")));batchSnapshot.set("file_names",json.valueToTree(manifest(row).stream().map(TaskStorage.Input::name).toList()));
            data.put("result",detection.batchView(row.batchId(),batchSnapshot.toString()));
        }
        return data;
    }
    public void execute(TaskRepository.Row row,BooleanSupplier ownerAlive){
        long started=System.nanoTime();var freshOutputs=new ArrayList<String>();
        try{
            cleanupOutputs(row.outputs());tx.executeWithoutResult(status->{tasks.requireAttempt(row.id(),row.token());tasks.clearOutputs(row.id(),row.token());});
            tx.executeWithoutResult(status->records.requireActiveUser(new SessionPrincipal(row.userId(),"async-task","inspector")));
            Frozen frozen=frozen(row);var manifest=manifest(row);
            Runnable checkpoint=()->{
                if(!ownerAlive.getAsBoolean())throw new TaskFault("WORKER_STOPPING",true);
                if(!row.expires().isAfter(Instant.now()))throw new TaskFault("TASK_EXPIRED",false);
                if(System.nanoTime()-started>frozen.attemptTimeout().toNanos())throw new TaskFault("ATTEMPT_TIMEOUT",true);
                tasks.checkAttempt(row.id(),row.token());
            };
            checkpoint.run();var path=models.verify(frozen.model().storageKey(),frozen.model().bytes(),frozen.model().sha256());guard.inspect(path,frozen.model().sha256());modelService.validateTaskSnapshot(frozen.model());
            try(var lease=runtime.acquireFrozen(frozen.model(),frozen.device(),frozen.inference(),()->sessions.openFrozen(path,frozen.device(),frozen.inference()))){
                var actor=new SessionPrincipal(row.userId(),"async-task","inspector");var prepared=new ArrayList<DetectionService.Prepared>();
                for(int i=0;i<manifest.size();i++){
                    checkpoint.run();var upload=decoder.read(inputs.open(row.id(),i,manifest.get(i)));if(!upload.sha256().equals(manifest.get(i).sha256()))throw new TaskFault("INPUT_CHANGED",false);
                    var image=decoder.decode(upload.bytes());final int completed=i;
                    var item=detection.prepareTask(actor,lease,image,upload.sha256(),frozen.inference(),checkpoint,id->{
                        freshOutputs.add(id);tx.executeWithoutResult(status->{tasks.requireAttempt(row.id(),row.token());tasks.checkpoint(row.id(),row.token(),completed,encode(freshOutputs));});
                    });prepared.add(item);
                    final int done=i+1;tx.executeWithoutResult(status->{tasks.requireAttempt(row.id(),row.token());tasks.checkpoint(row.id(),row.token(),done,encode(freshOutputs));});progress.store(tasks.find(row.id()));
                }
                checkpoint.run();String batchId=UUID.randomUUID().toString();var batchSnapshot=json.createObjectNode();batchSnapshot.set("model",json.valueToTree(Map.of("id",frozen.model().id(),"name",frozen.model().name(),"version",frozen.model().version())));
                batchSnapshot.set("inference_config",tree(prepared.get(0).row().config()));batchSnapshot.set("file_names",json.valueToTree(manifest.stream().map(TaskStorage.Input::name).toList()));
                tx.executeWithoutResult(status->{
                    tasks.requireAttempt(row.id(),row.token());records.requireActiveUser(actor);
                    records.insertBatch(batchId,row.userId(),row.modelId(),null,row.digest(),batchSnapshot.toString(),row.imageCount());
                    for(int i=0;i<prepared.size();i++)records.insert(prepared.get(i).row(),prepared.get(i).stats(),batchId,i);
                    tasks.succeed(row.id(),row.token(),batchId);audit.success(row.userId(),"batch_detect",Map.of("task_id",row.id(),"batch_id",batchId,"model_id",row.modelId(),"image_count",row.imageCount(),"actual_device",frozen.device().actual()));
                });
            }
        }catch(RuntimeException failure){
            for(String id:freshOutputs)cleanupOutput(id);
            String code=code(failure);boolean retry=failure instanceof TaskFault fault?fault.retryable():!(failure instanceof BusinessException b&&(b.getStatus().value()==400||b.getStatus().value()==403));
            try{tx.executeWithoutResult(status->{
                if(tasks.failedAttempt(row,code,retry,Instant.now().plus(config.retryDelay().multipliedBy(1L<<Math.min(4,row.attempts()-1))))){
                    var changed=tasks.find(row.id());audit.taskFailure(row.userId(),changed.terminal()?"task_failed":"task_retry",Map.of("task_id",row.id(),"attempt",row.attempts(),"error_code",code));
                }
            });}
            catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Task attempt outcome uncertain; will recover from DB; task={}",row.id());throw new TaskFault("WORKER_STATE_UNAVAILABLE",true);}
        }
        var latest=tasks.find(row.id());if(latest!=null){progress.store(latest);if(latest.terminal())cleanupInputs(latest);}
    }
    public void recoverInterrupted(){
        for(var row:tasks.interrupted()){tx.executeWithoutResult(status->tasks.recover(row));cleanupOutputs(row.outputs());}
        cleanupTerminal();
    }
    public void cleanupTerminal(){for(var row:tasks.needsCleanup())cleanupInputs(row);}
    private void cleanupInputs(TaskRepository.Row row){if(row.terminal()&&inputs.discard(row.id()))tasks.markCleaned(row.id());}
    private void cleanupOutputs(String encoded){for(String id:parse(encoded,new TypeReference<List<String>>(){}))cleanupOutput(id);}
    private void cleanupOutput(String id){try{UUID.fromString(id);if(records.find(id)==null)results.discard(id);}catch(RuntimeException unknown){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Task output cleanup deferred; record={}",id);}}
    private Frozen frozen(TaskRepository.Row row){
        var value=parse(row.snapshot(),new TypeReference<Frozen>(){});
        if(value.model()==null||value.device()==null||value.inference()==null||!validator.validate(value.inference()).isEmpty()||!Objects.equals(value.model().id(),row.modelId())||value.attemptTimeout()==null||value.attemptTimeout().compareTo(Duration.ofSeconds(1))<0||value.attemptTimeout().compareTo(Duration.ofMinutes(30))>0)throw new TaskFault("SNAPSHOT_INVALID",false);
        return value;
    }
    private List<TaskStorage.Input> manifest(TaskRepository.Row row){var result=parse(row.manifest(),new TypeReference<List<TaskStorage.Input>>(){});if(result.size()!=row.imageCount())throw new TaskFault("INPUT_INVALID",false);return result;}
    private static String code(RuntimeException failure){if(failure instanceof TaskFault fault)return fault.code();if(failure instanceof BusinessException business)return switch(business.getStatus().value()){case 400->"INPUT_OR_MODEL_INVALID";case 403->"USER_DISABLED";case 504->"NATIVE_TIMEOUT";default->"INFERENCE_UNAVAILABLE";};return "ATTEMPT_FAILED";}
    private static void sameDigest(TaskRepository.Row row,String digest){if(!row.digest().equals(digest))throw DetectionErrors.conflict();}
    private static String digest(List<TaskStorage.Input> inputs){try{var hash=MessageDigest.getInstance("SHA-256");hash.update("ssdd-task-v1".getBytes(StandardCharsets.US_ASCII));hash.update(java.nio.ByteBuffer.allocate(4).putInt(inputs.size()).array());for(var item:inputs){byte[] name=item.name().getBytes(StandardCharsets.UTF_8);hash.update(java.nio.ByteBuffer.allocate(4).putInt(name.length).array());hash.update(name);hash.update(HexFormat.of().parseHex(item.sha256()));}return HexFormat.of().formatHex(hash.digest());}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new TaskFault("SNAPSHOT_INVALID",false);}}
    private JsonNode tree(String value){try{return json.readTree(value);}catch(Exception ex){throw new TaskFault("SNAPSHOT_INVALID",false);}}
    private <T>T parse(String value,TypeReference<T> type){try{return json.readValue(value,type);}catch(Exception ex){throw new TaskFault("SNAPSHOT_INVALID",false);}}
}
