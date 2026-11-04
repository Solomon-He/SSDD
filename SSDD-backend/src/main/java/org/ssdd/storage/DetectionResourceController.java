package org.ssdd.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.ssdd.detection.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@RestController
public class DetectionResourceController {
    private final DetectionRepository repository;
    private final DetectionStorage storage;
    private final SignedUrls urls;
    public DetectionResourceController(DetectionRepository repository,DetectionStorage storage,SignedUrls urls) {this.repository=repository;this.storage=storage;this.urls=urls;}
    @GetMapping("/uploads/results/{id}/{kind}.png")
    ResponseEntity<Resource> read(@PathVariable UUID id,@PathVariable String kind,@RequestParam long expires,@RequestParam String signature) {
        urls.verify(id.toString(),kind,expires,signature);
        var resource=storage.resource(repository.resourceKey(id.toString(),kind));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy","no-referrer").header("X-Content-Type-Options","nosniff").body(resource);
    }
    @Component
    public static class SignedUrls {
        private final SecretKey key;
        private final DetectionProperties config;
        public SignedUrls(SecretKey jwtSigningKey,DetectionProperties config) {
            this.config=config;
            this.key=new SecretKeySpec(mac(jwtSigningKey,"ssdd-result-url-key-v1"),"HmacSHA256");
        }
        public String url(String id,String kind) {
            long expires=Instant.now().plus(config.resultUrlTtl()).getEpochSecond();
            return "/uploads/results/"+id+"/"+kind+".png?expires="+expires+"&signature="+signature(id,kind,expires);
        }
        public void verify(String id,String kind,long expires,String signature) {
            long now=Instant.now().getEpochSecond();
            if(!kind.matches("original|mask|overlay|class-[1-4]")||expires<=now||expires>now+config.resultUrlTtl().toSeconds()
                    ||signature==null||!signature.matches("[A-Za-z0-9_-]{43}")
                    ||!MessageDigest.isEqual(signature(id,kind,expires).getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)))throw DetectionErrors.missing();
        }
        private String signature(String id,String kind,long expires) {return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(key,"ssdd-result-v1\n"+id+"\n"+kind+"\n"+expires));}
        private static byte[] mac(SecretKey key,String message) {
            try {var mac=Mac.getInstance("HmacSHA256");mac.init(key);return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));}
            catch(java.security.GeneralSecurityException ex) {throw new IllegalStateException("Result URL signing unavailable",ex);}
        }
    }
}
