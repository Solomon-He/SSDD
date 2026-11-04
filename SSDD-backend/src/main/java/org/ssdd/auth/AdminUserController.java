package org.ssdd.auth;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.ssdd.common.api.*;

@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name="bearerAuth")
public class AdminUserController {
    private final AuthService service;
    public AdminUserController(AuthService service) { this.service=service; }
    @GetMapping
    ApiResponse<PageResult<AuthViews.AdminUser>> users(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required=false) String search,@RequestParam(required=false) String role) {
        return ApiResponse.ok(service.users(page,search,role));
    }
    @GetMapping("/{id}")
    ApiResponse<AuthViews.AdminUser> detail(@PathVariable String id) { return ApiResponse.ok(service.userDetail(id)); }
    @PutMapping("/{id}/role")
    ApiResponse<AuthViews.AdminUser> role(@AuthenticationPrincipal SessionPrincipal actor,@PathVariable String id,
            @Valid @RequestBody AuthRequests.Role input) {
        return ApiResponse.ok(service.changeRole(actor.userId(),id,input.role()),"角色修改成功");
    }
}
