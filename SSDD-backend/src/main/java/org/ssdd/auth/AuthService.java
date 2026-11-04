package org.ssdd.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssdd.audit.AuditService;
import org.ssdd.common.api.*;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.storage.AvatarStorage;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class AuthService {
    private final AuthMapper mapper;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AuthProperties config;
    private final AuditService audit;
    private final AvatarStorage avatars;
    private final org.ssdd.ratelimit.AuthRateLimiter limiter;
    private final String dummyHash;
    public AuthService(AuthMapper mapper,PasswordEncoder passwords,TokenService tokens,AuthProperties config,AuditService audit,AvatarStorage avatars,org.ssdd.ratelimit.AuthRateLimiter limiter) {
        this.mapper=mapper;this.passwords=passwords;this.tokens=tokens;this.config=config;this.audit=audit;this.avatars=avatars;
        this.limiter=limiter;dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    @Transactional
    public String register(AuthRequests.Register input) { return create(input,"inspector"); }

    private String create(AuthRequests.Register input,String role) {
        passwordPolicy(input.password());
        String name=input.displayName();
        if(name==null)name=input.email().substring(0,input.email().indexOf('@'));
        if(name.length()>50)name=name.substring(0,50);
        if(name.isBlank())throw bad("显示名称不能为空");
        String id=UUID.randomUUID().toString();
        mapper.insertUser(id,input.email(),passwords.encode(input.password()));
        mapper.insertProfile(UUID.randomUUID().toString(),id,role,name);
        audit.success(id,"register",Map.of("role",role));
        return id;
    }
    @Transactional
    public boolean bootstrap(AuthRequests.Register input) {
        mapper.lockAdminManagement();
        var existing=mapper.lockByEmail(input.email());
        if(existing!=null) {
            if(!existing.role().equals("admin")||!existing.status().equals("active"))throw new IllegalStateException("初始化邮箱已被非活动管理员账号占用，不自动改写账号");
            return false;
        }
        create(input,"admin");return true;
    }
    @Transactional
    public AuthViews.Login login(AuthRequests.Login input,String ip,String agent) {
        loginPasswordPolicy(input.password());
        var user=mapper.lockByEmail(input.email());
        boolean matches=passwords.matches(input.password(),user==null?dummyHash:user.passwordHash());
        if(user==null||!matches||!user.status().equals("active"))throw unauthorized("邮箱或密码错误");
        String sid=UUID.randomUUID().toString(),refresh=tokens.randomRefresh();
        Instant expires=Instant.now().plus(config.refreshTtl());
        mapper.insertSession(sid,user.id(),utc(expires),truncate(ip,45),truncate(agent,512));
        mapper.insertToken(UUID.randomUUID().toString(),sid,TokenService.hash(refresh),utc(expires));
        limiter.success(input.email());
        audit.success(user.id(),"login",Map.of());
        return new AuthViews.Login(view(user),tokens.issue(user.id(),sid,refresh,expires));
    }
    @Transactional(noRollbackFor=BusinessException.class)
    public AuthViews.Login refresh(String refresh) {
        var found=mapper.token(TokenService.hash(refresh));
        if(found==null)throw unauthorized("refresh_token 无效或已过期，请重新登录");
        // Lock order is user -> session -> token for login/password/refresh consistency.
        var user=mapper.lockUser(found.userId());
        var session=mapper.lockSession(found.sessionId());
        var token=mapper.lockToken(found.id());
        if(token.consumedAt()!=null) {
            mapper.revokeSession(session.id());
            audit.success(user.id(),"refresh_reuse",Map.of("session_id",session.id()));
            throw unauthorized("检测到刷新令牌重复使用，请重新登录");
        }
        var now=LocalDateTime.now(ZoneOffset.UTC);
        if(!user.status().equals("active")||session.revokedAt()!=null||!session.expiresAt().isAfter(now)
                ||token.revokedAt()!=null||!token.expiresAt().isAfter(now))throw unauthorized("refresh_token 无效或已过期，请重新登录");
        String nextId=UUID.randomUUID().toString(),next=tokens.randomRefresh();
        mapper.insertToken(nextId,session.id(),TokenService.hash(next),session.expiresAt());
        mapper.consume(token.id(),nextId);
        return new AuthViews.Login(view(user),tokens.issue(user.id(),session.id(),next,session.expiresAt().toInstant(ZoneOffset.UTC)));
    }
    public AuthViews.User me(String id) { return view(require(mapper.user(id))); }

    @Transactional
    public void logout(SessionPrincipal principal) {
        mapper.lockUser(principal.userId());
        mapper.revokeSession(principal.sessionId());
        audit.success(principal.userId(),"logout",Map.of());
    }
    @Transactional
    public void changePassword(String id,AuthRequests.Password input) {
        passwordPolicy(input.newPassword());loginPasswordPolicy(input.oldPassword());
        var user=require(mapper.lockUser(id));
        if(!passwords.matches(input.oldPassword(),user.passwordHash()))throw bad("当前密码错误");
        if(input.oldPassword().equals(input.newPassword()))throw bad("新密码不能与旧密码相同");
        mapper.password(id,passwords.encode(input.newPassword()));mapper.revokeAll(id);
        audit.success(id,"password_change",Map.of());
    }
    @Transactional
    public AuthViews.Profile updateProfile(String id,Map<String,com.fasterxml.jackson.databind.JsonNode> input) {
        if(input==null||input.isEmpty()||!Set.of("displayName","avatarUrl").containsAll(input.keySet()))throw bad("仅支持修改显示名称和头像");
        var user=require(mapper.lockUser(id));
        String name=user.displayName(),avatar=user.avatarStorageKey();
        if(input.containsKey("displayName")) {
            var value=input.get("displayName");
            if(value==null||!value.isTextual()||value.asText().strip().isEmpty()||value.asText().strip().length()>50)throw bad("显示名称必须为 1～50 个字符");
            name=value.asText().strip();
        }
        if(input.containsKey("avatarUrl")) {
            var value=input.get("avatarUrl");
            if(value==null||value.isNull()||(value.isTextual()&&value.asText().isBlank()))avatar=null;
            else {
                if(!value.isTextual())throw bad("头像地址格式错误");
                String url=value.asText();
                if(url.equals(avatarUrl(avatar))) { /* keep own current managed avatar */ }
                else {
                    try {
                        var uri=java.net.URI.create(url);
                        if(url.length()>512||!("http".equals(uri.getScheme())||"https".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null)throw bad("头像地址必须为 http/https URL");
                    } catch(IllegalArgumentException ex) { throw bad("头像地址格式错误"); }
                    avatar=url;
                }
            }
        }
        mapper.profile(id,name,avatar);
        if(!Objects.equals(avatar,user.avatarStorageKey()))avatars.afterCommitDelete(id,user.avatarStorageKey());
        audit.success(id,"profile_update",Map.of());
        return profile(require(mapper.user(id)));
    }
    @Transactional
    public AuthViews.Avatar setAvatar(String id,AvatarStorage.Prepared prepared) {
        var user=require(mapper.lockUser(id));
        mapper.profile(id,user.displayName(),prepared.key());
        avatars.afterCommitDelete(id,user.avatarStorageKey());
        audit.success(id,"profile_update",Map.of("avatar_updated",true));
        var result=profile(require(mapper.user(id)));
        return new AuthViews.Avatar(result.avatarUrl(),result);
    }
    public PageResult<AuthViews.AdminUser> users(PageQuery page,String search,String role) {
        if(search!=null) { search=search.strip();if(search.isEmpty())search=null;else if(search.length()>100)throw bad("搜索内容过长"); }
        if(role!=null&&!Set.of("admin","inspector").contains(role))throw bad("无效的角色值");
        var result=mapper.page(page.toMybatisPage(),search,role);
        return PageResult.of(result.getRecords().stream().map(u->admin(u,null)).toList(),result.getTotal(),result.getCurrent(),result.getSize());
    }
    public AuthViews.AdminUser userDetail(String profileId) {
        var user=require(mapper.byProfileId(profileId));return admin(user,mapper.detectionCount(user.id()));
    }
    @Transactional
    public AuthViews.AdminUser changeRole(String actorId,String profileId,String role) {
        mapper.lockAdminManagement();
        var actor=require(mapper.lockUser(actorId));
        if(!actor.status().equals("active")||!actor.role().equals("admin"))throw new BusinessException(HttpStatus.FORBIDDEN,"权限不足");
        var target=require(mapper.byProfileId(profileId));
        if(target.id().equals(actorId))throw bad("不能修改自己的角色");
        target=require(mapper.lockUser(target.id()));
        if(!Set.of("admin","inspector").contains(role))throw bad("无效的角色值");
        if(target.role().equals("admin")&&role.equals("inspector")&&mapper.activeAdmins()<=1)throw bad("不能移除最后一个活动管理员");
        mapper.role(target.id(),role);
        audit.success(actorId,"user_role_change",Map.of("target_user_id",target.id(),"old_role",target.role(),"new_role",role));
        return admin(require(mapper.user(target.id())),null);
    }
    static void passwordPolicy(String value) {
        if(value==null||value.length()<6)throw bad("密码长度不能少于 6 位");
        loginPasswordPolicy(value);
    }
    private static void loginPasswordPolicy(String value) {
        if(value==null||value.isBlank()||value.getBytes(StandardCharsets.UTF_8).length>72)throw bad("密码 UTF-8 编码长度不能超过 72 字节");
    }
    static BusinessException bad(String message) { return new BusinessException(HttpStatus.BAD_REQUEST,message); }
    static BusinessException unauthorized(String message) { return new BusinessException(HttpStatus.UNAUTHORIZED,message); }
    private static AuthMapper.UserRow require(AuthMapper.UserRow user) { if(user==null)throw new BusinessException(HttpStatus.NOT_FOUND,"用户不存在");return user; }
    private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant,ZoneOffset.UTC); }
    private static String truncate(String value,int max) { return value==null?null:value.substring(0,Math.min(value.length(),max)); }
    static String avatarUrl(String key) { return key==null?null:key.startsWith("avatars/")?"/uploads/"+key:key; }
    private static AuthViews.Profile profile(AuthMapper.UserRow u) {
        return new AuthViews.Profile(u.profileId(),u.id(),u.role(),u.displayName(),avatarUrl(u.avatarStorageKey()),u.createdAt().toInstant(ZoneOffset.UTC),u.updatedAt().toInstant(ZoneOffset.UTC));
    }
    private static AuthViews.User view(AuthMapper.UserRow u) { return new AuthViews.User(u.id(),u.email(),profile(u)); }
    private static AuthViews.AdminUser admin(AuthMapper.UserRow u,Long count) {
        return new AuthViews.AdminUser(u.profileId(),u.id(),u.role(),u.displayName(),avatarUrl(u.avatarStorageKey()),u.createdAt().toInstant(ZoneOffset.UTC),u.updatedAt().toInstant(ZoneOffset.UTC),u.email(),count);
    }
}
