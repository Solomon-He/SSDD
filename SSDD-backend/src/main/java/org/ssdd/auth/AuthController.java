package org.ssdd.auth;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.audit.AuditService;
import org.ssdd.common.api.ApiResponse;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.ratelimit.AuthRateLimiter;
import org.ssdd.storage.AvatarStorage;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthRateLimiter limiter;
    private final AuditService audit;
    private final AvatarStorage avatars;
    public AuthController(AuthService auth,AuthRateLimiter limiter,AuditService audit,AvatarStorage avatars) {
        this.auth=auth;this.limiter=limiter;this.audit=audit;this.avatars=avatars;
    }
    @Operation(summary="注册质检员账号")
    @PostMapping("/register")
    ResponseEntity<ApiResponse<AuthViews.Registered>> register(@Valid @RequestBody AuthRequests.Register input,HttpServletRequest request) {
        limiter.register(request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(new AuthViews.Registered(auth.register(input)),"注册成功，请登录"));
    }
    @Operation(summary="登录")
    @PostMapping("/login")
    ApiResponse<AuthViews.Login> login(@Valid @RequestBody AuthRequests.Login input,HttpServletRequest request) {
        limiter.login(input.email(),request.getRemoteAddr());
        try {
            return ApiResponse.ok(auth.login(input,request.getRemoteAddr(),request.getHeader("User-Agent")));
        } catch(BusinessException ex) {
            if(ex.getStatus()==HttpStatus.UNAUTHORIZED) {
                limiter.failure(input.email());
                try { audit.failure("login",Map.of("account_hash",TokenService.hash(input.email()))); }
                catch(RuntimeException loggingFailure) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Failed login audit could not be persisted"); }
            }
            throw ex;
        }
    }
    @Operation(summary="轮换刷新令牌")
    @PostMapping("/refresh")
    ApiResponse<AuthViews.Login> refresh(@Valid @RequestBody AuthRequests.Refresh input,HttpServletRequest request) {
        limiter.refresh(request.getRemoteAddr());return ApiResponse.ok(auth.refresh(input.refreshToken()));
    }
    @SecurityRequirement(name="bearerAuth")
    @GetMapping("/me")
    ApiResponse<AuthViews.User> me(@AuthenticationPrincipal SessionPrincipal user) { return ApiResponse.ok(auth.me(user.userId())); }
    @SecurityRequirement(name="bearerAuth")
    @PostMapping("/logout")
    ApiResponse<Void> logout(@AuthenticationPrincipal SessionPrincipal user) { auth.logout(user);return ApiResponse.ok(null,"登出成功"); }
    @SecurityRequirement(name="bearerAuth")
    @PutMapping("/password")
    ApiResponse<Void> password(@AuthenticationPrincipal SessionPrincipal user,@Valid @RequestBody AuthRequests.Password input) {
        auth.changePassword(user.userId(),input);return ApiResponse.ok(null,"密码修改成功，请重新登录");
    }
    @SecurityRequirement(name="bearerAuth")
    @PutMapping("/profile")
    ApiResponse<AuthViews.Profile> profile(@AuthenticationPrincipal SessionPrincipal user,@RequestBody Map<String,JsonNode> input) {
        return ApiResponse.ok(auth.updateProfile(user.userId(),input),"个人信息修改成功");
    }
    @SecurityRequirement(name="bearerAuth")
    @PostMapping(value="/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<AuthViews.Avatar> avatar(@AuthenticationPrincipal SessionPrincipal user,@RequestPart("avatar") MultipartFile file) {
        var prepared=avatars.prepare(user.userId(),file);
        try { return ApiResponse.ok(auth.setAvatar(user.userId(),prepared),"头像上传成功"); }
        catch(RuntimeException ex) { avatars.discard(user.userId(),prepared);throw ex; }
    }
}
