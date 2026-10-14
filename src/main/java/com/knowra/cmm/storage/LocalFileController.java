package com.knowra.cmm.storage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * local·dev 파일 서빙 — 운영에선 S3 가 직접 준다. {@code public/} 아래만 연다(운영 버킷 정책과 같게).
 * Content-Type 은 확장자로 정하고 nosniff — 올린 파일이 문서로 해석되지 않게.
 */
@RestController
@Profile("!prod")
public class LocalFileController {

    private final LocalDiskStorageService storage;

    public LocalFileController(LocalDiskStorageService storage) { this.storage = storage; }

    @GetMapping("/api/files/**")
    public ResponseEntity<Resource> serve(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String key = uri.substring(uri.indexOf("/api/files/") + "/api/files/".length());
        if (!key.startsWith(StorageService.PUBLIC_PREFIX + "/") || key.contains("..")) return ResponseEntity.status(403).build();
        Path file = storage.root().resolve(key).normalize();
        if (!file.startsWith(storage.root()) || !Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(StorageKeys.safeContentType(key)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }
}
