package org.ssdd.data;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.ssdd.audit.*;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.detection.*;
import java.util.*;

@Service
public class ExportService {
    private final DetectionRepository detections;
    private final LogRepository logs;
    private final AuditService audit;
    private final CsvFiles files;
    private final TransactionTemplate read,write;
    private static final int PAGE_SIZE=200,MAX_ROWS=10000;
    private static final Map<String,String> ACTIONS=Map.ofEntries(Map.entry("detect","单张检测"),Map.entry("batch_detect","批量检测"),Map.entry("login","用户登录"),Map.entry("logout","用户登出"),Map.entry("register","用户注册"),Map.entry("model_activate","激活模型"),Map.entry("model_switch","切换激活模型"),Map.entry("model_upload","上传模型"),Map.entry("model_update","修改模型"),Map.entry("model_delete","删除模型"),Map.entry("export_records","导出记录"),Map.entry("profile_update","修改资料"),Map.entry("password_change","修改密码"),Map.entry("user_role_change","修改角色"),Map.entry("refresh_reuse","刷新令牌重放"));
    public ExportService(DetectionRepository detections,LogRepository logs,AuditService audit,CsvFiles files,PlatformTransactionManager manager) {
        this.detections=detections;this.logs=logs;this.audit=audit;this.files=files;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);write=new TransactionTemplate(manager);
    }
    public ResponseEntity<Resource> detections(SessionPrincipal actor,String user,String search,String start,String end) {
        var scope=detections.scope(actor,user,search,DateRange.parse(start,end));boolean admin=actor.role().equals("admin");long[] count={0};
        var headers=new ArrayList<>(List.of("检测时间","操作者"));if(admin)headers.add("邮箱");headers.addAll(List.of("使用模型","模型版本","缺陷区域数","凹坑","边缘裂纹","划痕刮伤","氧化铁皮","缺陷面积占比","推理耗时(ms)","原图存储键"));
        var prepared=files.generate("detection_records",headers,csv->read.executeWithoutResult(status->{
            count[0]=detections.count(scope);limit(count[0]);
            for(long offset=0;offset<count[0];offset+=PAGE_SIZE) {
                var page=detections.exportPage(scope,offset,PAGE_SIZE);var stats=detections.statsFor(page.stream().map(r->r.record().id()).toList());
                for(var item:page) {
                    var row=item.record();var values=new ArrayList<Object>(List.of(row.createdAt().toString(),row.displayName()));if(admin)values.add(item.email());
                    values.addAll(List.of(row.modelName(),row.modelVersion(),row.defectCount()));var regions=new int[4];for(var s:stats.getOrDefault(row.id(),List.of()))regions[s.classIndex()-1]=s.regions();for(int value:regions)values.add(value);
                    values.addAll(List.of(String.format(Locale.ROOT,"%.6f%%",100.0*row.defectPixels()/row.totalPixels()),row.millis(),row.originalKey()));CsvFiles.row(csv,values);
                }
            }
        }));
        return publishAudit(actor,prepared,"detections",count[0]);
    }
    public ResponseEntity<Resource> logs(SessionPrincipal actor,LogRepository.Filter filters) {
        var scope=logs.scope(actor,filters);boolean admin=actor.role().equals("admin");long[] count={0};
        var headers=new ArrayList<>(List.of("操作时间","操作者"));if(admin)headers.add("邮箱");headers.addAll(List.of("操作类型","操作状态","IP 地址","客户端","操作详情","请求 ID"));
        var prepared=files.generate("operation_logs",headers,csv->read.executeWithoutResult(status->{
            count[0]=logs.count(scope);limit(count[0]);
            for(long offset=0;offset<count[0];offset+=PAGE_SIZE)for(var row:logs.page(scope,offset,PAGE_SIZE)) {
                var values=new ArrayList<Object>(List.of(row.created().toString(),row.name()));if(admin)values.add(row.email());
                values.addAll(Arrays.asList(ACTIONS.getOrDefault(row.action(),row.action()),row.status().equals("success")?"成功":"失败",row.ip(),row.agent(),row.details(),row.requestId()));CsvFiles.row(csv,values);
            }
        }));
        return publishAudit(actor,prepared,"logs",count[0]);
    }
    private ResponseEntity<Resource> publishAudit(SessionPrincipal actor,CsvFiles.Prepared prepared,String type,long count) {
        try {write.executeWithoutResult(status->audit.success(actor.userId(),"export_records",Map.of("export_type",type,"row_count",count)));return prepared.response();}
        catch(RuntimeException ex){prepared.discard();throw ex;}
    }
    private static void limit(long count){if(count>MAX_ROWS)throw DetectionErrors.bad("导出超过 10000 条，请缩小筛选范围");}
}
