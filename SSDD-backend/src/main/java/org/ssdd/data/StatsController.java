package org.ssdd.data;

import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.ssdd.cache.StatsCache;
import org.ssdd.common.api.ApiResponse;

@RestController
@SecurityRequirement(name="bearerAuth")
public class StatsController {
    private final StatsCache cache;
    public StatsController(StatsCache cache){this.cache=cache;}
    @GetMapping("/api/admin/stats") ApiResponse<StatsRepository.Summary> stats(){return ApiResponse.ok(cache.get());}
}
