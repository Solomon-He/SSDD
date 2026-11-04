package org.ssdd.model;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.audit.AuditService;
import org.ssdd.inference.*;
import org.ssdd.storage.ModelStorage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ModelService {
    private final ModelRepository models;
    private final ModelStorage files;
    private final OnnxModelGuard guard;
    private final InferenceSessionFactory sessions;
    private final ModelRuntime runtime;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final String contract;
    public ModelService(ModelRepository models,ModelStorage files,OnnxModelGuard guard,InferenceSessionFactory sessions,
                        ModelRuntime runtime,AuditService audit,PlatformTransactionManager transactionManager,ObjectMapper json) throws IOException {
        this.models=models;this.files=files;this.guard=guard;this.sessions=sessions;this.runtime=runtime;this.audit=audit;
        this.tx=new TransactionTemplate(transactionManager);this.json=json;
        this.contract=new ClassPathResource("inference/v2-contract.json").getContentAsString(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> list() {
        var state=models.state();return models.list().stream().map(row->view(row,state.activeModelId())).toList();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(String id) { return view(models.find(id),models.state().activeModelId()); }

    public Map<String,Object> upload(String actor,MultipartFile file,String name,String version,String description,String architecture,Integer classes) {
        name=text(name,100,false); version=version==null||version.isBlank()?"1.0.0":text(version,50,false);
        description=text(description,2000,true);
        if(!"v2".equals(architecture)||(classes!=null&&classes!=4))throw ModelErrors.bad("只支持 V2 四通道模型");
        try(var change=runtime.beginChange()) {
            runtime.requireCapacity();
            String id=UUID.randomUUID().toString();var stored=files.store(id,file);
            boolean committed=false, attemptedPublication=false;
            try {
                var path=files.verify(stored.key(),stored.size(),stored.sha256());
                String source=guard.inspect(path,stored.sha256());
                sessions.validateOnCpu(path);
                var config=(ObjectNode)parse(contract);config.put("validation_source",source);
                var row=new ModelRepository.ModelRow(id,name,version,stored.fileName(),stored.key(),stored.sha256(),stored.size(),
                        config.toString(),description,actor,null,null,null,null,null,null,null,null,null);
                attemptedPublication=true;
                var result=tx.execute(status->{
                    models.requireAdmin(actor);models.insert(row);
                    audit.success(actor,"model_upload",Map.of("model_id",id,"sha256",stored.sha256(),"bytes",stored.size()));
                    return view(models.find(id),models.state().activeModelId());
                });
                committed=true;return result;
            } finally {
                if(!committed) {
                    if(!attemptedPublication)files.delete(stored.key());
                    else {
                        try { models.find(id); } // A commit acknowledgement may be lost; keep any published file.
                        catch(org.ssdd.common.exception.BusinessException ex) {
                            if(ex.getStatus()==org.springframework.http.HttpStatus.NOT_FOUND)files.delete(stored.key());
                        } catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Upload commit uncertain; retaining model file for reconciliation; model={}",id); }
                    }
                }
            }
        }
    }

    public Map<String,Object> activate(String actor,String id) {
        try(var change=runtime.beginChange()) {
            runtime.requireCapacity();
            var expected=models.state();var row=models.find(id);
            var path=files.verify(row.storageKey(),row.bytes(),row.sha256());
            guard.inspect(path,row.sha256());validateSavedContract(row);
            var candidate=sessions.openAndWarm(path);boolean published=false;
            ModelRuntime.Entry old=null;
            try {
                var publication=runtime.publication(()->{
                    var next=tx.execute(status->{
                        var state=models.lockState();if(state.version()!=expected.version())throw ModelErrors.conflict();
                        models.requireAdmin(actor);var current=models.lock(id);
                        if(!sameFile(row,current))throw ModelErrors.conflict();
                        models.activate(id);
                        audit.success(actor,"model_activate",Map.of("model_id",id,"actual_device",candidate.deviceInfo().actual()));
                        return new Publication(new ModelRuntime.Entry(current,candidate,state.version()+1),view(current,id));
                    });
                    var previous=runtime.install(next.entry());return new Installed(previous,next.response());
                });
                published=true;old=publication.old();return publication.response();
            } finally {
                if(!published)closeCandidate(candidate);
                runtime.retireIfIdle(old);
            }
        }
    }
    private record Publication(ModelRuntime.Entry entry,Map<String,Object> response) {}
    private record Installed(ModelRuntime.Entry old,Map<String,Object> response) {}

    public Map<String,Object> update(String actor,String id,Map<String,JsonNode> input) {
        Set<String> allowed=Set.of("name","version","description","miou","precision","recall","dice","fps","metric_source","file_size");
        if(input==null||input.isEmpty()||!allowed.containsAll(input.keySet()))throw ModelErrors.bad("未提供有效的模型更新字段");
        try(var change=runtime.beginChange()) {
            return runtime.publication(()->{
                var updated=tx.execute(status->{
                    var state=models.lockState();models.requireAdmin(actor);var old=models.lock(id);
                    String name=field(input,"name",old.name(),100,false),version=field(input,"version",old.version(),50,false);
                    String description=field(input,"description",old.description(),2000,true);
                    String source=field(input,"metric_source",old.metricSource(),255,true);
                    Double miou=metric(input,"miou",old.miou(),1),precision=metric(input,"precision",old.precision(),1);
                    Double recall=metric(input,"recall",old.recall(),1),dice=metric(input,"dice",old.dice(),1),fps=metric(input,"fps",old.fps(),Double.MAX_VALUE);
                    metric(input,"file_size",null,Double.MAX_VALUE); // Legacy clients may send it, but measured bytes remain authoritative.
                    if((miou!=null||precision!=null||recall!=null||dice!=null||fps!=null)&&(source==null||source.isBlank()))
                        throw ModelErrors.bad("填写模型指标时必须提供 metric_source 来源说明");
                    var row=new ModelRepository.ModelRow(old.id(),name,version,old.fileName(),old.storageKey(),old.sha256(),old.bytes(),
                            old.inferenceConfig(),description,old.uploadedBy(),miou,precision,recall,dice,fps,source,old.createdAt(),old.updatedAt(),null);
                    models.update(row);audit.success(actor,"model_update",Map.of("model_id",id));
                    return models.find(id);
                });
                runtime.updateSnapshot(updated);
                return view(updated,models.state().activeModelId());
            });
        }
    }

    public void delete(String actor,String id) {
        try(var change=runtime.beginChange()) {
            var removed=tx.execute(status->{
                var state=models.lockState();models.requireAdmin(actor);var row=models.lock(id);
                if(id.equals(state.activeModelId()))throw ModelErrors.bad("不能删除当前激活模型");
                boolean keep=models.referenced(id);
                models.softDelete(id);audit.success(actor,"model_delete",Map.of("model_id",id,"historical_weights_retained",keep));
                return new Removed(row,keep);
            });
            if(!removed.keep())runtime.whenUnused(id,()->{
                // An in-flight detection can publish its historical reference before releasing its lease.
                if(!models.referenced(id))files.delete(removed.row().storageKey());
            });
        }
    }
    private record Removed(ModelRepository.ModelRow row,boolean keep) {}

    public void restore() {
        try(var change=runtime.beginChange()) {
            var expected=models.state();
            if(expected.activeModelId()==null) { runtime.restoreFailure("NO_ACTIVE_MODEL");return; }
            runtime.requireCapacity();var row=models.find(expected.activeModelId());
            var path=files.verify(row.storageKey(),row.bytes(),row.sha256());guard.inspect(path,row.sha256());validateSavedContract(row);
            var candidate=sessions.openAndWarm(path);boolean published=false;
            ModelRuntime.Entry old=null;
            try {
                old=runtime.publication(()->{
                    var latest=models.state();if(!latest.equals(expected))throw ModelErrors.conflict();
                    return runtime.install(new ModelRuntime.Entry(row,candidate,expected.version()));
                });
                published=true;
                org.slf4j.LoggerFactory.getLogger(getClass()).info("Active model restored; model={} device={}",row.id(),candidate.deviceInfo().actual());
            } finally { if(!published)closeCandidate(candidate);runtime.retireIfIdle(old); }
        } catch(RuntimeException failure) {
            runtime.restoreFailure("MODEL_RESTORE_FAILED");
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Model restore unavailable; type={}",failure.getClass().getSimpleName());
        }
    }

    private static void closeCandidate(InferenceSession candidate) {
        try { candidate.close(); }catch(RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(ModelService.class).warn("Candidate Session cleanup failed"); }
    }
    private static boolean sameFile(ModelRepository.ModelRow a,ModelRepository.ModelRow b) {
        return a.storageKey().equals(b.storageKey())&&a.sha256().equals(b.sha256())&&a.bytes()==b.bytes()&&a.inferenceConfig().equals(b.inferenceConfig());
    }
    private void validateSavedContract(ModelRepository.ModelRow row) {
        var saved=parse(row.inferenceConfig());
        if(!(saved instanceof ObjectNode object))throw ModelErrors.bad("模型推理配置无效");
        var comparable=object.deepCopy();comparable.remove("validation_source");
        if(!comparable.equals(parse(contract)))
            throw ModelErrors.bad("模型推理契约版本不受支持");
    }
    private JsonNode parse(String value) {
        try { return json.readTree(value); }catch(IOException ex) { throw ModelErrors.bad("模型推理配置无效"); }
    }
    private static String text(String value,int max,boolean nullable) {
        if(value==null) { if(nullable)return null;throw ModelErrors.bad("模型名称和版本不能为空"); }
        value=value.strip();if(value.length()>max||(!nullable&&value.isBlank()))throw ModelErrors.bad("模型文本字段长度无效");return value;
    }
    private static String field(Map<String,JsonNode> input,String key,String original,int max,boolean nullable) {
        if(!input.containsKey(key))return original;
        var value=input.get(key);
        if(value==null||value.isNull())return text(null,max,nullable);
        if(!value.isTextual())throw ModelErrors.bad("模型文本字段类型无效");return text(value.asText(),max,nullable);
    }
    private static Double metric(Map<String,JsonNode> input,String key,Double original,double max) {
        if(!input.containsKey(key))return original;
        var value=input.get(key);if(value==null||value.isNull())return null;
        if(!value.isNumber()||!Double.isFinite(value.asDouble())||value.asDouble()<0||value.asDouble()>max)throw ModelErrors.bad("模型指标范围无效");
        return value.asDouble();
    }
    private Map<String,Object> view(ModelRepository.ModelRow row,String active) {
        var view=new LinkedHashMap<String,Object>();
        view.put("id",row.id());view.put("name",row.name());view.put("version",row.version());view.put("arch_version","v2");
        view.put("file_name",row.fileName());view.put("file_path",row.storageKey());view.put("num_classes",4);
        view.put("file_sha256",row.sha256());view.put("file_size_bytes",row.bytes());view.put("file_size",row.bytes()/(1024.0*1024));
        view.put("description",row.description());view.put("status",row.id().equals(active)?"active":"inactive");view.put("uploaded_by",row.uploadedBy());
        view.put("miou",row.miou());view.put("precision",row.precision());view.put("recall",row.recall());view.put("dice",row.dice());view.put("fps",row.fps());
        view.put("metric_source",row.metricSource());view.put("inference_config",parse(row.inferenceConfig()));view.put("created_at",row.createdAt());view.put("updated_at",row.updatedAt());
        return Collections.unmodifiableMap(view);
    }
}
