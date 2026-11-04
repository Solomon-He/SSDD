package org.ssdd.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.ssdd.common.api.ApiResponse;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Configuration
public class SecurityConfiguration {
    @Bean SecretKey jwtSigningKey(AuthProperties properties) {
        byte[] key;
        try { key=Base64.getDecoder().decode(properties.signingKey()); }
        catch(IllegalArgumentException ex) { throw new IllegalStateException("SSDD_JWT_SIGNING_KEY 必须是 Base64 编码的至少 32 字节随机密钥"); }
        if(key.length<32) throw new IllegalStateException("SSDD_JWT_SIGNING_KEY 必须包含至少 32 字节随机密钥");
        return new SecretKeySpec(key,"HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKey key,AuthProperties properties) {
        var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(15)),
                new JwtIssuerValidator(properties.issuer()),new JwtClaimValidator<Object>("exp",Objects::nonNull),
                new JwtClaimValidator<List<String>>("aud",a->a!=null&&a.contains(properties.audience()))));
        return decoder;
    }
    @Bean PasswordEncoder passwordEncoder(AuthProperties properties) { return new BCryptPasswordEncoder(properties.bcryptStrength()); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,AuthMapper mapper,ObjectMapper json) throws Exception {
        var resolver=new DefaultBearerTokenResolver();
        http.csrf(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a->a.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                        .requestMatchers("/api/auth/register","/api/auth/login","/api/auth/refresh","/health","/actuator/health/**",
                                "/uploads/avatars/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html","/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,"/api/models","/api/models/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,"/api/models","/api/models/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,"/api/models","/api/models/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
                .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->securityError(s,json,401))
                        .accessDeniedHandler((r,s,x)->securityError(s,json,403)))
                .oauth2ResourceServer(o->o.bearerTokenResolver(request->isPublic(request.getRequestURI().substring(request.getContextPath().length()))?null:resolver.resolve(request))
                        .authenticationEntryPoint((r,s,x)->securityError(s,json,x instanceof OAuth2AuthenticationException oauth
                                &&oauth.getError().getErrorCode().equals("temporarily_unavailable")?503:401))
                        .accessDeniedHandler((r,s,x)->securityError(s,json,403))
                        .jwt(j->j.jwtAuthenticationConverter(jwt->{
                            String uid=jwt.getSubject(),sid=jwt.getClaimAsString("sid");
                            if(!isUuid(uid)||!isUuid(sid)) throw invalid();
                            AuthMapper.UserRow user;
                            try { user=mapper.activeSessionUser(sid,uid); }
                            catch(DataAccessException ex) { throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable")); }
                            if(user==null) throw invalid();
                            return new SessionAuthentication(new SessionPrincipal(uid,sid,user.role()));
                        })));
        return http.build();
    }
    private static boolean isPublic(String path) {
        return Set.of("/api/auth/register","/api/auth/login","/api/auth/refresh","/health").contains(path)
                ||path.startsWith("/uploads/avatars/")||path.startsWith("/uploads/results/")||path.startsWith("/actuator/health")
                ||path.startsWith("/v3/api-docs")||path.startsWith("/swagger-ui");
    }
    private static boolean isUuid(String value) { return value!=null&&value.matches("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}"); }
    private static OAuth2AuthenticationException invalid() { return new OAuth2AuthenticationException(new OAuth2Error("invalid_token")); }
    private static void securityError(HttpServletResponse response,ObjectMapper json,int code) throws IOException {
        response.setStatus(code);response.setContentType(MediaType.APPLICATION_JSON_VALUE);response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(),ApiResponse.failure(code==401?"未认证或会话已过期":code==403?"权限不足":"认证服务暂不可用"));
    }
    static final class SessionAuthentication extends AbstractAuthenticationToken {
        private final SessionPrincipal principal;
        SessionAuthentication(SessionPrincipal principal) {
            super(List.of(new SimpleGrantedAuthority("ROLE_"+principal.role().toUpperCase(Locale.ROOT))));
            this.principal=principal;setAuthenticated(true);
        }
        @Override public Object getCredentials() { return ""; }
        @Override public Object getPrincipal() { return principal; }
        @Override public String getName() { return principal.userId(); }
    }
}
