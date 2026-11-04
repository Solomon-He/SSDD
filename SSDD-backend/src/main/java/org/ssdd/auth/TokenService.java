package org.ssdd.auth;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final SecureRandom random=new SecureRandom();
    public TokenService(JwtEncoder encoder,AuthProperties properties) { this.encoder=encoder;this.properties=properties; }
    public AuthViews.Session issue(String userId,String sid,String refresh,Instant sessionExpiry) {
        Instant now=Instant.now(),expiry=now.plus(properties.accessTtl());
        if(expiry.isAfter(sessionExpiry)) expiry=sessionExpiry;
        var claims=JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(userId).claim("sid",sid).issuedAt(now).expiresAt(expiry).id(UUID.randomUUID().toString()).build();
        var header=JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return new AuthViews.Session(encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue(),refresh,expiry.getEpochSecond());
    }
    public String randomRefresh() { byte[] value=new byte[32];random.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
