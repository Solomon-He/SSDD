package org.ssdd.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.common.api.ApiResponse;
import org.ssdd.inference.ModelRuntime;
import org.ssdd.audit.AuditService;
import org.ssdd.common.exception.BusinessException;
import java.util.*;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/models")
@SecurityRequirement(name="bearerAuth")
public class ModelController {
    private final ModelService models;
    private final ModelRuntime runtime;
    private final AuditService audit;
    public ModelController(ModelService models,ModelRuntime runtime,AuditService audit) { this.models=models;this.runtime=runtime;this.audit=audit; }
    @GetMapping ApiResponse<List<Map<String,Object>>> list() { return ApiResponse.ok(models.list()); }
    @GetMapping("/runtime") ApiResponse<Map<String,Object>> runtime() { return ApiResponse.ok(runtime.status()); }
    @GetMapping("/{id}") ApiResponse<Map<String,Object>> detail(@PathVariable UUID id) { return ApiResponse.ok(models.detail(id.toString())); }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<Map<String,Object>>> upload(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestPart("model") MultipartFile file,@RequestParam String name,@RequestParam(required=false) String version,
            @RequestParam(required=false) String description,@RequestParam(name="arch_version",defaultValue="v2") String architecture,
            @RequestParam(name="num_classes",required=false) Integer classes) {
        return ResponseEntity.status(201).body(ApiResponse.ok(change("model_upload",actor,null,
                ()->models.upload(actor.userId(),file,name,version,description,architecture,classes)),"模型上传成功"));
    }
    @PutMapping("/{id}/activate") ApiResponse<Map<String,Object>> activate(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id) {
        return ApiResponse.ok(change("model_activate",actor,id,()->models.activate(actor.userId(),id.toString())),"模型已激活并加载");
    }
    @PutMapping("/{id}") ApiResponse<Map<String,Object>> update(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id,@RequestBody Map<String,JsonNode> input) {
        return ApiResponse.ok(change("model_update",actor,id,()->models.update(actor.userId(),id.toString(),input)),"模型信息已更新");
    }
    @DeleteMapping("/{id}") ApiResponse<Void> delete(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id) {
        change("model_delete",actor,id,()->{models.delete(actor.userId(),id.toString());return null;});return ApiResponse.ok(null,"模型已删除");
    }
    private <T> T change(String action,SessionPrincipal actor,UUID id,Supplier<T> operation) {
        try { return operation.get(); }
        catch(RuntimeException failure) {
            var details=new LinkedHashMap<String,Object>();
            if(id!=null)details.put("model_id",id.toString());
            details.put("error_status",failure instanceof BusinessException business?business.getStatus().value():500);
            try { audit.failure(actor.userId(),action,details); }
            catch(RuntimeException unavailable) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Model failure audit unavailable; action={}",action); }
            throw failure;
        }
    }
}
