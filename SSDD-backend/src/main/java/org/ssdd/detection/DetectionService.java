package org.ssdd.detection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.audit.AuditService;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.common.api.*;
import org.ssdd.inference.*;
import org.ssdd.storage.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class DetectionService {
    private final DetectionRepository records;
    private final DetectionStorage files;
    private final DetectionImageDecoder decoder;
    private final V2DetectionPostprocessor postprocessor;
    private final ModelRuntime runtime;
    private final InferenceProperties inference;
    private final DetectionProperties config;
    private final BatchProperties batches;
    private final DetectionRateLimiter limiter;
    private final DetectionResourceController.SignedUrls urls;
    private final AuditService audit;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final Semaphore slots;
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    public DetectionService(DetectionRepository records,DetectionStorage files,DetectionImageDecoder decoder,
            V2DetectionPostprocessor postprocessor,ModelRuntime runtime,InferenceProperties inference,DetectionProperties config,
            DetectionRateLimiter limiter,DetectionResourceController.SignedUrls urls,AuditService audit,ObjectMapper json,PlatformTransactionManager manager,BatchProperties batches) {
        this.records=records;this.files=files;this.decoder=decoder;this.postprocessor=postprocessor;this.runtime=runtime;this.inference=inference;
        this.config=config;this.limiter=limiter;this.urls=urls;this.audit=audit;this.json=json;this.transaction=new TransactionTemplate(manager);
        this.slots=new Semaphore(inference.maxConcurrency());
        this.batches=batches;
    }
    public Map<String,Object> detect(SessionPrincipal actor,MultipartFile file,String key) {
        return execute(actor,Collections.singletonList(file),key,false);
    }
    public Map<String,Object> batch(SessionPrincipal actor,List<MultipartFile> images,String key) {
        if(images==null||images.isEmpty()||images.size()>batches.maxImages())throw DetectionErrors.bad("批量图片数量必须在 1～"+batches.maxImages()+" 之间");
        return execute(actor,images,key,true);
    }
    private record Validated(String name,String sha256) {}
    private record Prepared(DetectionRepository.Row row,List<DetectionRepository.Stats> stats) {}
    private Map<String,Object> execute(SessionPrincipal actor,List<MultipartFile> images,String key,boolean batch) {
        if(key!=null&&!key.matches("[A-Za-z0-9._:-]{1,128}"))throw DetectionErrors.bad("Idempotency-Key 仅接受 1～128 位 ASCII 字母、数字、点、下划线、冒号和短横线");
        limiter.check(actor.userId());
        String reservation=key==null?null:(batch?"batch:":"single:")+actor.userId()+":"+key;
        if(reservation!=null&&!pending.add(reservation))throw DetectionErrors.conflict();
        boolean admitted=false;var ids=new ArrayList<String>();
        long started=System.nanoTime();
        try {
            if(!slots.tryAcquire())throw DetectionErrors.busy();admitted=true;
            var manifest=new ArrayList<Validated>();DetectionImageDecoder.Image singleImage=null;
            for(var file:images) {
                var upload=decoder.read(file);
                if(!batch) {
                    var previous=records.byKey(actor.userId(),key);
                    if(previous!=null){if(!previous.digest().equals(upload.sha256()))throw DetectionErrors.conflict();return view(previous,false);}
                }
                var decoded=decoder.decode(upload.bytes()); // Validate every file before any native execution.
                if(!batch)singleImage=decoded;
                manifest.add(new Validated(filename(file.getOriginalFilename(),manifest.size()),upload.sha256()));budget(started,batch);
            }
            String digest=batch?batchDigest(manifest):manifest.get(0).sha256();
            if(batch) {
                var previous=records.batchByKey(actor.userId(),key);
                if(previous!=null){if(!previous.digest().equals(digest))throw DetectionErrors.conflict();return batchView(previous.id(),previous.snapshot());}
            }
            try(var lease=runtime.acquire()) {
                var model=lease.model();var device=lease.device();String batchId=batch?UUID.randomUUID().toString():null;
                var prepared=new ArrayList<Prepared>();
                for(int index=0;index<images.size();index++) {
                    var image=singleImage;
                    if(batch) {
                        var upload=decoder.read(images.get(index));
                        if(!upload.sha256().equals(manifest.get(index).sha256()))throw DetectionErrors.bad("上传图片内容在处理中发生变化");
                        image=decoder.decode(upload.bytes());
                    }
                    prepared.add(prepare(actor,lease,image,key,batch,manifest.get(index).sha256(),started,ids));
                }
                var batchSnapshot=json.createObjectNode();batchSnapshot.set("model",mapToNode(model.id(),model.name(),model.version()));
                var common=(com.fasterxml.jackson.databind.node.ObjectNode)tree(prepared.get(0).row().config());common.remove(List.of("image_format","encoded_width","encoded_height","exif_orientation"));
                batchSnapshot.set("inference_config",common);batchSnapshot.set("file_names",json.valueToTree(manifest.stream().map(Validated::name).toList()));
                budget(started,batch);
                transaction.executeWithoutResult(status->{
                    records.requireActiveUser(actor);
                    if(batch)records.insertBatch(batchId,actor.userId(),model.id(),key,digest,batchSnapshot.toString(),prepared.size());
                    for(int index=0;index<prepared.size();index++) {
                        var item=prepared.get(index);records.insert(item.row(),item.stats(),batchId,index);
                    }
                    audit.success(actor.userId(),batch?"batch_detect":"detect",batch?Map.of("batch_id",batchId,"model_id",model.id(),"actual_device",device.actual(),"image_count",prepared.size())
                            :Map.of("record_id",prepared.get(0).row().id(),"model_id",model.id(),"actual_device",device.actual(),"defect_count",prepared.get(0).row().defectCount()));
                });
                org.slf4j.LoggerFactory.getLogger(getClass()).info("Detection published; batch={}, count={}, device={}, totalMs={}",batch,prepared.size(),device.actual(),(System.nanoTime()-started)/1_000_000.0);
                return batch?batchView(batchId,batchSnapshot.toString()):view(Objects.requireNonNull(records.find(prepared.get(0).row().id())),false);
            }
        } catch(RuntimeException failure) {
            ids.forEach(this::compensateUnlessPublished);
            try {audit.failure(actor.userId(),batch?"batch_detect":"detect",Map.of("error_status",failure instanceof org.ssdd.common.exception.BusinessException business?business.getStatus().value():500));}
            catch(RuntimeException ignored) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Detection failure audit unavailable");}
            throw failure;
        } finally {if(admitted)slots.release();if(reservation!=null)pending.remove(reservation);}
    }
    private Prepared prepare(SessionPrincipal actor,ModelRuntime.Lease lease,DetectionImageDecoder.Image image,String key,boolean batch,String digest,long started,List<String> ids) {
                var model=lease.model();var device=lease.device();
                float[] tensor=decoder.tensor(image.rgb());budget(started,batch);
                var nativeResult=lease.runTimed(tensor);float[] logits=nativeResult.logits();
                double millis=nativeResult.inferenceMillis();budget(started,batch);
                var output=postprocessor.process(logits,image.rgb());budget(started,batch);
                String id=UUID.randomUUID().toString();ids.add(id);var keys=files.save(id,output.images());budget(started,batch);
                var snapshot=json.createObjectNode();
                snapshot.set("model_contract",tree(model.inferenceConfig()));
                snapshot.put("model_sha256",model.sha256());snapshot.put("model_id",model.id());
                snapshot.put("model_name",model.name());snapshot.put("model_version",model.version());
                snapshot.put("requested_device",device.requested());snapshot.put("actual_device",device.actual());
                snapshot.put("fallback_reason",device.fallbackReason());snapshot.put("runtime_version",device.runtimeVersion());
                snapshot.put("device_id",inference.deviceId());snapshot.put("precision","fp32");snapshot.put("use_tf32",false);
                snapshot.put("gpu_memory_limit_bytes",inference.gpuMemoryLimitBytes());
                snapshot.put("image_format",image.format());snapshot.put("encoded_width",image.encodedWidth());snapshot.put("encoded_height",image.encodedHeight());
                snapshot.put("exif_orientation",image.orientation());snapshot.put("orientation_applied",true);
                snapshot.put("resize","stretch_half_pixel_bilinear_float_rgb_v1");snapshot.put("alpha_background","#ffffff");
                snapshot.put("mask_restore","nearest_floor_v1");snapshot.put("overlay_alpha",V2DetectionPostprocessor.ALPHA);
                snapshot.put("region_count_space","model_1600x256");snapshot.put("pixel_count_space","oriented_original");
                var channels=new LinkedHashMap<String,String>();for(int c=1;c<=4;c++)channels.put(Integer.toString(c),keys.get("class-"+c));
                var row=new DetectionRepository.Row(id,actor.userId(),model.id(),batch?null:key,digest,keys.get("original"),keys.get("mask"),keys.get("overlay"),
                        encode(channels),image.rgb().getWidth(),image.rgb().getHeight(),output.totalPixels(),output.unionPixels(),output.count(),millis,
                        model.name(),model.version(),snapshot.toString(),Instant.now(),null,null,null);
                var stats=new ArrayList<DetectionRepository.Stats>();for(int c=0;c<4;c++)stats.add(new DetectionRepository.Stats(c+1,output.components()[c],output.pixels()[c]));
                return new Prepared(row,stats);
    }
    private JsonNode mapToNode(String id,String name,String version){return json.valueToTree(Map.of("id",id,"name",name,"version",version));}
    private Map<String,Object> batchView(String id,String snapshot) {
        var saved=tree(snapshot);var rows=records.batchRows(id);var stats=records.statsFor(rows.stream().map(DetectionRepository.Row::id).toList());var results=new ArrayList<Map<String,Object>>();
        for(int i=0;i<rows.size();i++){var item=view(rows.get(i),false,stats.getOrDefault(rows.get(i).id(),List.of()));item.put("file_name",saved.path("file_names").get(i).asText());results.add(item);}
        return Map.of("batch_id",id,"model",saved.path("model"),"total",rows.size(),"results",results);
    }
    private static String filename(String input,int index) {
        if(input==null||input.isBlank())return "image-"+(index+1);
        input=input.replace('\\','/');input=input.substring(input.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]"," ").strip();
        return input.isBlank()?"image-"+(index+1):input.substring(0,input.offsetByCodePoints(0,Math.min(200,input.codePointCount(0,input.length()))));
    }
    private static String batchDigest(List<Validated> manifest) {
        try {
            var hash=java.security.MessageDigest.getInstance("SHA-256");hash.update("ssdd-batch-v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            hash.update(java.nio.ByteBuffer.allocate(4).putInt(manifest.size()).array());
            for(var item:manifest){byte[] name=item.name().getBytes(java.nio.charset.StandardCharsets.UTF_8);hash.update(java.nio.ByteBuffer.allocate(4).putInt(name.length).array());hash.update(name);hash.update(HexFormat.of().parseHex(item.sha256()));}
            return HexFormat.of().formatHex(hash.digest());
        } catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private void budget(long started,boolean batch) {if(System.nanoTime()-started>(batch?batches.processingTimeout():config.processingTimeout()).toNanos())throw DetectionErrors.timeout();}
    private void compensateUnlessPublished(String id) {
        try {if(records.find(id)==null)files.discard(id);}
        catch(RuntimeException unknown) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Detection commit outcome unavailable; retaining files for reconciliation; record={}",id);}
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(SessionPrincipal actor,String id) {
        var row=records.find(id);
        if(row==null||(!actor.role().equals("admin")&&!row.userId().equals(actor.userId())))throw DetectionErrors.missing();
        return view(row,true);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> list(SessionPrincipal actor,PageQuery page,String user,String search,boolean all) {
        return list(actor,page,user,search,all,null,null);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> list(SessionPrincipal actor,PageQuery page,String user,String search,boolean all,String start,String end) {
        var scope=records.scope(actor,user,search,org.ssdd.data.DateRange.parse(start,end));long count=records.count(scope);
        if(all&&count>10000)throw DetectionErrors.bad("全量查询超过 10000 条，请缩小筛选范围");
        long number=all?1:page.page(),size=all?Math.max(1,count):page.pageSize();
        var rows=records.list(scope,all?0:(number-1)*size,all?10000:page.pageSize());var stats=records.statsFor(rows.stream().map(DetectionRepository.Row::id).toList());
        var items=rows.stream().map(r->view(r,true,stats.getOrDefault(r.id(),List.of()))).toList();
        return Map.of("items",items,"total",count,"page",number,"pageSize",size,"totalPages",count/size+(count%size==0?0:1));
    }
    private Map<String,Object> view(DetectionRepository.Row row,boolean history) {
        return view(row,history,records.stats(row.id()));
    }
    private Map<String,Object> view(DetectionRepository.Row row,boolean history,List<DetectionRepository.Stats> statistics) {
        var data=new LinkedHashMap<String,Object>();data.put("record_id",row.id());
        data.put("model",Map.of("id",row.modelId(),"name",row.modelName(),"version",row.modelVersion()));
        data.put("original_image_url",urls.url(row.id(),"original"));data.put("mask_image_url",urls.url(row.id(),"mask"));data.put("overlay_image_url",urls.url(row.id(),"overlay"));
        var channelUrls=new LinkedHashMap<String,String>();for(int c=1;c<=4;c++)channelUrls.put(Integer.toString(c),urls.url(row.id(),"class-"+c));data.put("channel_mask_urls",channelUrls);
        var counts=new LinkedHashMap<String,Integer>();var pixels=new LinkedHashMap<String,Long>();
        for(int c=1;c<=4;c++){counts.put(Integer.toString(c),0);pixels.put(Integer.toString(c),0L);}
        for(var stats:statistics){counts.put(Integer.toString(stats.classIndex()),stats.regions());pixels.put(Integer.toString(stats.classIndex()),stats.pixels());}
        data.put("defect_count",row.defectCount());data.put("defect_class_counts",counts);
        data.put("defect_info",Map.of("total_pixels",row.totalPixels(),"defect_pixels",row.defectPixels(),"defect_ratio",row.defectPixels()/(double)row.totalPixels(),"class_distribution",pixels));
        data.put("inference_time_ms",row.millis());data.put("inference_config_snapshot",tree(row.config()));
        if(history) {
            data.put("id",row.id());data.put("user_id",row.userId());data.put("model_id",row.modelId());data.put("created_at",row.createdAt());
            data.put("models",Map.of("name",row.modelName(),"version",row.modelVersion()));
            var profile=new LinkedHashMap<String,Object>();profile.put("role",row.role());profile.put("display_name",row.displayName());
            profile.put("avatar_url",row.avatarKey()==null?null:"/uploads/"+row.avatarKey());data.put("profiles",profile);
        }
        return data;
    }
    private JsonNode tree(String source) {try{return json.readTree(source);}catch(JsonProcessingException ex){throw new IllegalStateException("Invalid stored inference snapshot",ex);}}
    private String encode(Object source) {try{return json.writeValueAsString(source);}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}}
}
