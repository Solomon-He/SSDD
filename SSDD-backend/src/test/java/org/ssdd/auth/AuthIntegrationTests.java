package org.ssdd.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.util.*;
import org.springframework.core.io.ByteArrayResource;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.ratelimit.*;
import org.ssdd.support.MySqlTestDatabase;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("mysqlAvailable")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "ssdd.bootstrap-admin.enabled=false", "ssdd.auth.bcrypt-strength=10",
        "ssdd.rate-limit.window=2s", "ssdd.rate-limit.cooldown=2s", "ssdd.rate-limit.max-failures=3",
        "ssdd.rate-limit.login-ip-limit=5000", "ssdd.rate-limit.login-account-limit=100",
        "ssdd.rate-limit.register-ip-limit=5000", "ssdd.rate-limit.refresh-ip-limit=5000"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AuthIntegrationTests {
    private static final String PASSWORD="P2-test-password!";
    private static final String NAMESPACE="ssdd:test:"+UUID.randomUUID().toString().replace("-","");
    private static final Path STORAGE=Path.of("target","p2-avatars-"+UUID.randomUUID()).toAbsolutePath();
    private static MySqlTestDatabase database;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService service;
    @Autowired StringRedisTemplate redis;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired AuthProperties authConfig;

    static boolean mysqlAvailable() { return MySqlTestDatabase.available(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
        database=MySqlTestDatabase.open();
        r.add("ssdd.database.url",database::url);r.add("ssdd.database.username",database::username);r.add("ssdd.database.password",database::password);
        r.add("ssdd.rate-limit.namespace",()->NAMESPACE);r.add("ssdd.storage.root",()->STORAGE.toString());
        r.add("ssdd.auth.signing-key",()->Base64.getEncoder().encodeToString("P2-tests-only-key-not-for-production-1234".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @AfterAll static void cleanup(@Autowired StringRedisTemplate redis) throws Exception {
        try(var keys=redis.scan(ScanOptions.scanOptions().match(NAMESPACE+":*").count(100).build())) {
            while(keys.hasNext())redis.delete(keys.next());
        } finally {
            if(database!=null)database.close();
            if(Files.exists(STORAGE))try(var paths=Files.walk(STORAGE)) {
                for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);
            }
        }
    }

    @Test void registrationCanonicalizesEmailAndNeverAcceptsAdminRole() {
        String email=email();
        var registered=call("/api/auth/register",HttpMethod.POST,Map.of("email",email.toUpperCase(Locale.ROOT),"password",PASSWORD,"displayName","质检员🙂","role","admin"),null);
        expect(registered,201);
        var session=login(email,PASSWORD);
        assertEquals(email,session.data().path("user").path("email").asText());
        assertEquals("inspector",session.data().path("user").path("profile").path("role").asText());
        assertNotEquals(session.userId(),session.profileId());
        assertEquals("质检员🙂",session.data().path("user").path("profile").path("display_name").asText());
        String stored=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,session.userId());
        assertNotEquals(PASSWORD,stored);assertTrue(stored.startsWith("$2"));
        assertEquals(TokenService.hash(session.refresh()),jdbc.queryForObject("SELECT t.token_hash FROM auth_refresh_tokens t JOIN auth_sessions s ON s.id=t.session_id WHERE s.user_id=?",String.class,session.userId()));
        assertFalse(session.data().toString().contains("password_hash"));
        assertTrue(session.data().path("session").path("expires_at").asLong()>Instant.now().getEpochSecond());
        expect(call("/api/auth/register",HttpMethod.POST,Map.of("email",email,"password",PASSWORD),null),409);
    }

    @Test void validatesPasswordBytesAndRequiredFields() {
        expect(call("/api/auth/register",HttpMethod.POST,Map.of("email","invalid","password",PASSWORD),null),400);
        expect(call("/api/auth/register",HttpMethod.POST,Map.of("email",email(),"password","short"),null),400);
        expect(call("/api/auth/register",HttpMethod.POST,Map.of("email",email(),"password","密".repeat(25)),null),400);
        String email=email();register(email,"密".repeat(24));login(email,"密".repeat(24));
    }

    @Test void enforcesJwtSignatureExpiryIssuerAudienceAndPublicLoginIndependence() {
        var session=inspector();
        expect(call("/api/auth/me",HttpMethod.GET,null,null),401);
        expect(call("/api/auth/me",HttpMethod.GET,null,"invalid.jwt.value"),401);
        String[] parts=session.access().split("\\.");
        parts[2]=(parts[2].startsWith("A")?"B":"A")+parts[2].substring(1);
        expect(call("/api/auth/me",HttpMethod.GET,null,String.join(".",parts)),401);
        var jwt=decoder.decode(session.access());String sid=jwt.getClaimAsString("sid");
        expect(call("/api/auth/me",HttpMethod.GET,null,signed(session.userId(),sid,Instant.now().minusSeconds(120),authConfig.issuer(),authConfig.audience())),401);
        expect(call("/api/auth/me",HttpMethod.GET,null,signed(session.userId(),sid,Instant.now().plusSeconds(60),"wrong-issuer",authConfig.audience())),401);
        expect(call("/api/auth/me",HttpMethod.GET,null,signed(session.userId(),sid,Instant.now().plusSeconds(60),authConfig.issuer(),"wrong-audience")),401);
        var publicLogin=call("/api/auth/login",HttpMethod.POST,Map.of("email",session.email(),"password",PASSWORD),"stale.invalid.token");
        expect(publicLogin,200);assertNull(publicLogin.getHeaders().getFirst("Set-Cookie"));
    }

    @Test void rotatesRefreshAndCommitsSessionRevocationOnReplay() {
        var first=inspector();
        var refreshed=refresh(first.refresh());expect(refreshed,200);
        String next=refreshed.getBody().path("data").path("session").path("refresh_token").asText();
        assertNotEquals(first.refresh(),next);
        expect(call("/api/auth/me",HttpMethod.GET,null,first.access()),200);
        expect(refresh(first.refresh()),401);
        expect(call("/api/auth/me",HttpMethod.GET,null,refreshed.getBody().path("data").path("session").path("access_token").asText()),401);
        expect(refresh(next),401);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NOT NULL",Integer.class,first.userId()));
    }

    @Test void concurrentRefreshIssuesAtMostOneRotationAndRevokesReplayedFamily() throws Exception {
        var first=inspector();
        var results=concurrent(2,()->refresh(first.refresh()).getStatusCode().value());
        assertEquals(List.of(200,401),results.stream().sorted().toList());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_tokens t JOIN auth_sessions s ON s.id=t.session_id WHERE s.user_id=?",Integer.class,first.userId()));
        expect(call("/api/auth/me",HttpMethod.GET,null,first.access()),401);
    }

    @Test void logoutRevokesOnlyCurrentSession() {
        var first=inspector();var other=login(first.email(),PASSWORD);
        expect(call("/api/auth/logout",HttpMethod.POST,null,first.access()),200);
        expect(call("/api/auth/me",HttpMethod.GET,null,first.access()),401);
        expect(refresh(first.refresh()),401);
        expect(call("/api/auth/me",HttpMethod.GET,null,other.access()),200);
    }

    @Test void passwordChangeChecksOldPasswordAndRevokesAllSessions() {
        var first=inspector();var other=login(first.email(),PASSWORD);
        expect(call("/api/auth/password",HttpMethod.PUT,Map.of("oldPassword","wrong","newPassword","Updated-p2-password!"),first.access()),400);
        expect(call("/api/auth/me",HttpMethod.GET,null,first.access()),200);
        expect(call("/api/auth/password",HttpMethod.PUT,Map.of("oldPassword",PASSWORD,"newPassword",PASSWORD),first.access()),400);
        expect(call("/api/auth/password",HttpMethod.PUT,Map.of("oldPassword",PASSWORD,"newPassword","Updated-p2-password!"),first.access()),200);
        expect(call("/api/auth/me",HttpMethod.GET,null,other.access()),401);
        expect(refresh(first.refresh()),401);
        expect(call("/api/auth/login",HttpMethod.POST,Map.of("email",first.email(),"password",PASSWORD),null),401);
        login(first.email(),"Updated-p2-password!");
    }

    @Test void profilePatchDistinguishesMissingNullAndForbiddenFields() {
        var user=inspector();
        expect(call("/api/auth/profile",HttpMethod.PUT,Map.of(),user.access()),400);
        expect(call("/api/auth/profile",HttpMethod.PUT,Map.of("role","admin"),user.access()),400);
        expect(call("/api/auth/profile",HttpMethod.PUT,Map.of("displayName","  "),user.access()),400);
        expect(call("/api/auth/profile",HttpMethod.PUT,Map.of("avatarUrl","javascript:alert(1)"),user.access()),400);
        expect(call("/api/auth/profile",HttpMethod.PUT,Map.of("avatarUrl","/uploads/avatars/someone.png"),user.access()),400);
        var updated=call("/api/auth/profile",HttpMethod.PUT,Map.of("displayName"," 新名称 ","avatarUrl","https://example.test/avatar.png"),user.access());
        expect(updated,200);assertEquals("新名称",updated.getBody().path("data").path("display_name").asText());
        var input=new HashMap<String,Object>();input.put("avatarUrl",null);
        var cleared=call("/api/auth/profile",HttpMethod.PUT,input,user.access());expect(cleared,200);
        assertTrue(cleared.getBody().path("data").path("avatar_url").isNull());
        assertEquals("新名称",cleared.getBody().path("data").path("display_name").asText());
    }

    @Test void avatarsDecodeAllPromisedFormatsReencodeAndDeleteOldOwnedFile() throws Exception {
        var user=inspector();String previous=null;
        var files=List.of(image("png",3,3),image("jpeg",3,3),image("gif",3,3),
                Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAoAAAAdQslKUtv+BiOh/AAA="));
        for(byte[] file:files) {
            var response=upload(user.access(),file);expect(response,200);
            String url=response.getBody().path("data").path("avatar_url").asText();
            var picture=http.getForEntity(url,byte[].class);assertEquals(200,picture.getStatusCode().value());
            assertEquals(MediaType.IMAGE_PNG,picture.getHeaders().getContentType());
            assertNotNull(ImageIO.read(new ByteArrayInputStream(picture.getBody())));
            if(previous!=null)assertEquals(404,http.getForEntity(previous,byte[].class).getStatusCode().value());
            previous=url;
        }
        expect(upload(user.access(),"not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)),400);
        expect(upload(user.access(),new byte[2*1024*1024+1]),400);
        expect(upload(user.access(),image("png",5000,1)),400);
        var input=new HashMap<String,Object>();input.put("avatarUrl",null);
        expect(call("/api/auth/profile",HttpMethod.PUT,input,user.access()),200);
        assertEquals(404,http.getForEntity(previous,byte[].class).getStatusCode().value());
    }

    @Test void administratorBootstrapIsIdempotentAndDoesNotOverwriteCredentials() {
        String email=email();var input=new AuthRequests.Register(email,PASSWORD,"管理员");
        assertTrue(service.bootstrap(input));
        assertFalse(service.bootstrap(new AuthRequests.Register(email,"Other-password!","修改名")));
        var admin=login(email,PASSWORD);
        assertEquals("admin",admin.data().path("user").path("profile").path("role").asText());
        var inspector=inspector();
        assertThrows(IllegalStateException.class,()->service.bootstrap(new AuthRequests.Register(inspector.email(),PASSWORD,"bad promote")));
    }

    @Test void administratorEndpointsUseProfileIdsAndCurrentDatabaseRoles() {
        var admin=admin();var user=inspector();
        expect(call("/api/admin/users",HttpMethod.GET,null,user.access()),403);
        var detail=call("/api/admin/users/"+user.profileId(),HttpMethod.GET,null,admin.access());expect(detail,200);
        assertEquals(user.userId(),detail.getBody().path("data").path("user_id").asText());
        assertEquals(0,detail.getBody().path("data").path("detection_count").asInt());
        expect(call("/api/admin/users/"+user.userId(),HttpMethod.GET,null,admin.access()),404);
        var list=call("/api/admin/users?page=1&pageSize=2&role=inspector",HttpMethod.GET,null,admin.access());expect(list,200);
        assertEquals(2,list.getBody().path("data").path("pageSize").asInt());
        expect(call("/api/admin/users/"+admin.profileId()+"/role",HttpMethod.PUT,Map.of("role","inspector"),admin.access()),400);
        expect(call("/api/admin/users/"+user.profileId()+"/role",HttpMethod.PUT,Map.of("role","owner"),admin.access()),400);
        expect(call("/api/admin/users/"+user.profileId()+"/role",HttpMethod.PUT,Map.of("role","admin"),user.access()),403);
        expect(call("/api/admin/users/"+user.profileId()+"/role",HttpMethod.PUT,Map.of("role","admin"),admin.access()),200);
        expect(call("/api/admin/users",HttpMethod.GET,null,user.access()),200);
        expect(call("/api/admin/users/"+user.profileId()+"/role",HttpMethod.PUT,Map.of("role","inspector"),admin.access()),200);
        expect(call("/api/admin/users",HttpMethod.GET,null,user.access()),403);
    }

    @Test void concurrentAdministratorDemotionsRecheckActorAfterSerialization() throws Exception {
        var a=admin();var b=admin();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            Future<Integer> first=pool.submit(()->{gate.await();return call("/api/admin/users/"+b.profileId()+"/role",HttpMethod.PUT,Map.of("role","inspector"),a.access()).getStatusCode().value();});
            Future<Integer> second=pool.submit(()->{gate.await();return call("/api/admin/users/"+a.profileId()+"/role",HttpMethod.PUT,Map.of("role","inspector"),b.access()).getStatusCode().value();});
            gate.countDown();assertEquals(List.of(200,403),List.of(first.get(),second.get()).stream().sorted().toList());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_profiles WHERE user_id IN (?,?) AND role='admin'",Integer.class,a.userId(),b.userId()));
        } finally { pool.shutdownNow(); }
    }

    @Test void duplicateConcurrentRegistrationCreatesOneAccount() throws Exception {
        String email=email();var results=concurrent(2,()->call("/api/auth/register",HttpMethod.POST,Map.of("email",email,"password",PASSWORD),null).getStatusCode().value());
        assertEquals(List.of(201,409),results.stream().sorted().toList());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN user_profiles p ON p.user_id=u.id WHERE u.email=?",Integer.class,email));
    }

    @Test void disabledAndExpiredSessionsAreRejected() {
        var disabled=inspector();jdbc.update("UPDATE users SET status='disabled' WHERE id=?",disabled.userId());
        expect(call("/api/auth/me",HttpMethod.GET,null,disabled.access()),401);expect(refresh(disabled.refresh()),401);
        expect(call("/api/auth/login",HttpMethod.POST,Map.of("email",disabled.email(),"password",PASSWORD),null),401);
        var expired=inspector();jdbc.update("UPDATE auth_sessions SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 SECOND) WHERE user_id=?",expired.userId());
        expect(call("/api/auth/me",HttpMethod.GET,null,expired.access()),401);expect(refresh(expired.refresh()),401);
    }

    @Test void redisCooldownExpiresAndSuccessClearsFailureCounter() throws Exception {
        var user=inspector();
        for(int i=0;i<3;i++)expect(call("/api/auth/login",HttpMethod.POST,Map.of("email",user.email(),"password","wrong-password"),null),401);
        var blocked=call("/api/auth/login",HttpMethod.POST,Map.of("email",user.email(),"password",PASSWORD),null);
        expect(blocked,429);assertNotNull(blocked.getHeaders().getFirst("Retry-After"));
        assertTrue(redis.getExpire(NAMESPACE+":cooldown:"+TokenService.hash(user.email()),TimeUnit.MILLISECONDS)>0);
        Thread.sleep(2200);login(user.email(),PASSWORD);
        assertNull(redis.opsForValue().get(NAMESPACE+":failures:"+TokenService.hash(user.email())));
    }

    @Test void redisQuotaIsAtomicHasTtlAndRecovers() throws Exception {
        String namespace=NAMESPACE+":atomic";
        var config=new RateLimitProperties(true,namespace,Duration.ofSeconds(2),100,100,5,100,3,Duration.ofSeconds(2));
        var limiter=new AuthRateLimiter(redis,config,new MockEnvironment());
        var result=concurrent(20,()->{try{limiter.register("same-client");return 200;}catch(RateLimitException ex){return 429;}});
        assertEquals(5,result.stream().filter(i->i==200).count());
        Long ttl=redis.getExpire(limiter.key("register-ip","same-client"),TimeUnit.MILLISECONDS);
        assertNotNull(ttl);assertTrue(ttl>0&&ttl<=2000);
        Thread.sleep(2200);assertDoesNotThrow(()->limiter.register("same-client"));
    }

    @Test void loginQuotasSeparateAccountFromIpAndSuccessResetsFailures() {
        var properties=new RateLimitProperties(true,NAMESPACE+":dimensions",Duration.ofSeconds(30),2,1,10,10,3,Duration.ofSeconds(30));
        var limiter=new AuthRateLimiter(redis,properties,new MockEnvironment());
        limiter.login("a@example.test","first-ip");
        assertThrows(RateLimitException.class,()->limiter.login("a@example.test","second-ip"));
        limiter.login("b@example.test","first-ip");
        assertThrows(RateLimitException.class,()->limiter.login("c@example.test","first-ip"));
        limiter.failure("a@example.test");
        assertNotNull(redis.opsForValue().get(limiter.key("failures","a@example.test")));
        limiter.success("a@example.test");
        assertNull(redis.opsForValue().get(limiter.key("failures","a@example.test")));
    }

    @Test void avatarDatabaseFailureCompensatesNewFileAndPreservesOldFile() throws Exception {
        var user=inspector();
        var first=upload(user.access(),image("png",3,3));expect(first,200);
        String oldUrl=first.getBody().path("data").path("avatar_url").asText();
        jdbc.execute("CREATE TRIGGER p2_reject_profile BEFORE UPDATE ON user_profiles FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test database failure'");
        try {
            expect(upload(user.access(),image("png",4,4)),500);
            assertEquals(200,http.getForEntity(oldUrl,byte[].class).getStatusCode().value());
            try(var files=Files.list(STORAGE.resolve("avatars"))) {
                assertEquals(1,files.filter(p->p.getFileName().toString().startsWith(user.userId()+"_")).count());
            }
            assertEquals(oldUrl,call("/api/auth/me",HttpMethod.GET,null,user.access()).getBody().path("data").path("profile").path("avatar_url").asText());
        } finally { jdbc.execute("DROP TRIGGER p2_reject_profile"); }
    }

    @Test void redisOutageFailsClosedWithoutStoppingUserContainer() throws Exception {
        int unused;try(var socket=new java.net.ServerSocket(0)){unused=socket.getLocalPort();}
        var client=LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).shutdownTimeout(Duration.ZERO).build();
        var standalone=new org.springframework.data.redis.connection.RedisStandaloneConfiguration("127.0.0.1",unused);
        var factory=new LettuceConnectionFactory(standalone,client);factory.afterPropertiesSet();factory.start();
        try {
            var template=new StringRedisTemplate(factory);template.afterPropertiesSet();
            var properties=new RateLimitProperties(true,NAMESPACE+":offline",Duration.ofSeconds(2),2,2,2,2,2,Duration.ofSeconds(2));
            var limiter=new AuthRateLimiter(template,properties,new MockEnvironment());
            BusinessException error=assertThrows(BusinessException.class,()->limiter.login("offline@example.test","ip"));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,error.getStatus());
            try (var connection=redis.getConnectionFactory().getConnection()) {
                assertEquals("PONG",connection.ping());
            }
        } finally { factory.destroy(); }
    }

    @Test void writesAuditWithoutPasswordsOrTokenPlaintext() {
        var user=inspector();expect(call("/api/auth/logout",HttpMethod.POST,null,user.access()),200);
        var details=jdbc.queryForList("SELECT action,status,details FROM operation_logs WHERE user_id=? ORDER BY created_at",user.userId());
        assertTrue(details.size()>=3);
        String text=details.toString();assertFalse(text.contains(PASSWORD));assertFalse(text.contains(user.refresh()));assertFalse(text.contains(user.access()));
    }

    private record Login(String email,String userId,String profileId,String access,String refresh,JsonNode data) {
        @Override public String toString(){return "Login[credentials=REDACTED]";}
    }
    private Login inspector(){String email=email();register(email,PASSWORD);return login(email,PASSWORD);}
    private Login admin(){String email=email();service.bootstrap(new AuthRequests.Register(email,PASSWORD,"Test admin"));return login(email,PASSWORD);}
    private static String email(){return "p2-"+UUID.randomUUID()+"@example.test";}
    private void register(String email,String password){expect(call("/api/auth/register",HttpMethod.POST,Map.of("email",email,"password",password),null),201);}
    private Login login(String email,String password){
        var response=call("/api/auth/login",HttpMethod.POST,Map.of("email",email,"password",password),null);expect(response,200);
        var data=response.getBody().path("data");return new Login(email,data.path("user").path("id").asText(),data.path("user").path("profile").path("id").asText(),data.path("session").path("access_token").asText(),data.path("session").path("refresh_token").asText(),data);
    }
    private ResponseEntity<JsonNode> refresh(String token){return call("/api/auth/refresh",HttpMethod.POST,Map.of("refresh_token",token),null);}
    private ResponseEntity<JsonNode> call(String path,HttpMethod method,Object body,String token){
        var headers=new HttpHeaders();if(token!=null)headers.setBearerAuth(token);
        if(body!=null)headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private ResponseEntity<JsonNode> upload(String token,byte[] bytes){
        var body=new LinkedMultiValueMap<String,Object>();body.add("avatar",new ByteArrayResource(bytes){@Override public String getFilename(){return "untrusted-name.png";}});
        var headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/auth/avatar",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private String signed(String uid,String sid,Instant expiry,String issuer,String audience){
        var claims=JwtClaimsSet.builder().subject(uid).claim("sid",sid).issuer(issuer).audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(300)).expiresAt(expiry).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),claims)).getTokenValue();
    }
    private static byte[] image(String format,int width,int height)throws IOException{
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var output=new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image,format,output));return output.toByteArray();
    }
    private static void expect(ResponseEntity<?> response,int status){assertEquals(status,response.getStatusCode().value());}
    private static <T> List<T> concurrent(int count,Callable<T> action)throws Exception{
        var pool=Executors.newFixedThreadPool(count);var start=new CountDownLatch(1);var tasks=new ArrayList<Future<T>>();
        try{for(int i=0;i<count;i++)tasks.add(pool.submit(()->{start.await();return action.call();}));start.countDown();var values=new ArrayList<T>();for(var future:tasks)values.add(future.get(30,TimeUnit.SECONDS));return values;}
        finally{pool.shutdownNow();}
    }
}
