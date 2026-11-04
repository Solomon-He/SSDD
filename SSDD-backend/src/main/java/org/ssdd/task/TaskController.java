package org.ssdd.task;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.common.api.ApiResponse;
import java.util.*;

@RestController
@RequestMapping("/api/detect/tasks")
@SecurityRequirement(name="bearerAuth")
public class TaskController {
    private final TaskService tasks;
    public TaskController(TaskService tasks){this.tasks=tasks;}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<Map<String,Object>>> submit(@AuthenticationPrincipal SessionPrincipal actor,@RequestPart("images") List<MultipartFile> images,
            @RequestHeader(value="Idempotency-Key",required=false) String key){
        var result=tasks.submit(actor,images,key);
        return ResponseEntity.accepted().header(HttpHeaders.LOCATION,"/api/detect/tasks/"+result.get("task_id")).body(ApiResponse.ok(result));
    }
    @GetMapping("/{id}") ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id){return ApiResponse.ok(tasks.detail(actor,id.toString()));}
}
