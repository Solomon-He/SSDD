package org.ssdd.detection;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.common.api.*;
import java.util.*;

@RestController
@RequestMapping("/api/detect")
@SecurityRequirement(name="bearerAuth")
public class DetectionController {
    private final DetectionService service;
    private final org.ssdd.data.ExportService exports;
    public DetectionController(DetectionService service,org.ssdd.data.ExportService exports) {this.service=service;this.exports=exports;}
    @GetMapping("/records/export")
    org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> export(@AuthenticationPrincipal SessionPrincipal actor,
            @RequestParam(required=false) String userId,@RequestParam(required=false) String search,
            @RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){return exports.detections(actor,userId,search,startDate,endDate);}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String,Object>> detect(@AuthenticationPrincipal SessionPrincipal actor,@RequestPart("image") MultipartFile image,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {return ApiResponse.ok(service.detect(actor,image,key));}
    @PostMapping(value="/batch",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String,Object>> batch(@AuthenticationPrincipal SessionPrincipal actor,@RequestPart("images") List<MultipartFile> images,
            @RequestHeader(value="Idempotency-Key",required=false) String key){return ApiResponse.ok(service.batch(actor,images,key));}
    @GetMapping("/records")
    ApiResponse<Map<String,Object>> list(@AuthenticationPrincipal SessionPrincipal actor,@Valid @ModelAttribute PageQuery page,
            @RequestParam(required=false) String userId,@RequestParam(required=false) String search,@RequestParam(defaultValue="false") boolean all,
            @RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate) {
        return ApiResponse.ok(service.list(actor,page,userId,search,all,startDate,endDate));
    }
    @GetMapping("/records/{id}")
    ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable UUID id) {return ApiResponse.ok(service.detail(actor,id.toString()));}
}
