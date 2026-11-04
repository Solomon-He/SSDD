package org.ssdd.audit;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.ssdd.auth.SessionPrincipal;
import org.ssdd.common.api.*;
import org.ssdd.data.ExportService;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
@SecurityRequirement(name="bearerAuth")
public class LogController {
    private final LogService logs;
    private final ExportService exports;
    public LogController(LogService logs,ExportService exports){this.logs=logs;this.exports=exports;}
    @GetMapping ApiResponse<PageResult<Map<String,Object>>> list(@AuthenticationPrincipal SessionPrincipal actor,@Valid @ModelAttribute PageQuery page,@ModelAttribute LogRepository.Filter filter){return ApiResponse.ok(logs.list(actor,page,filter));}
    @GetMapping("/export") ResponseEntity<Resource> export(@AuthenticationPrincipal SessionPrincipal actor,@ModelAttribute LogRepository.Filter filter){return exports.logs(actor,filter);}
}
