package org.ssdd.storage;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;

@RestController
@Hidden
public class AvatarController {
    private final AvatarStorage storage;
    public AvatarController(AvatarStorage storage) { this.storage=storage; }
    @GetMapping("/uploads/avatars/{filename}")
    ResponseEntity<byte[]> avatar(@PathVariable String filename) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .body(storage.read(filename));
    }
}
