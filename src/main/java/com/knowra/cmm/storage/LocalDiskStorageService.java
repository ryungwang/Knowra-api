package com.knowra.cmm.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * local·dev 저장소 — {@code app.storage.local.dir} 아래에 같은 키 구조로 쓰고 {@link LocalFileController} 가
 * {publicBaseUrl}/api/files/{key} 로 보여 준다. 운영(S3)과 키·URL 모양을 맞춰 개발 중에만 깨지는 일이 없게.
 */
@Service
@Profile("!prod")
public class LocalDiskStorageService implements StorageService {

    private final StorageProperties properties;
    private final Path root;

    public LocalDiskStorageService(StorageProperties properties) {
        this.properties = properties;
        this.root = Path.of(properties.getLocal().getDir()).toAbsolutePath().normalize();
    }

    Path root() { return root; }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("잘못된 파일 경로");
        return target;
    }

    @Override
    public PublicFile storePublic(byte[] content, String path, String originalFilename) {
        String key = StorageKeys.publicKey(properties.getKeyPrefix(), path, originalFilename);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 저장하지 못했습니다.", e);
        }
        return new PublicFile(key, publicUrl(key), StorageKeys.safeContentType(key), content.length);
    }

    @Override
    public String publicUrl(String key) {
        return properties.getLocal().getPublicBaseUrl().replaceAll("/+$", "") + "/api/files/" + key;
    }

    @Override
    public String keyFromUrl(String url) {
        if (url == null) return null;
        int i = url.indexOf("/api/files/");
        return i < 0 ? null : url.substring(i + "/api/files/".length());
    }

    /** 로컬엔 비공개 객체가 없다 — 같은 주소를 돌려준다 */
    @Override
    public String presignedUrl(String key, Duration ttl) { return publicUrl(key); }

    @Override
    public byte[] download(String key) {
        try { return Files.readAllBytes(resolve(key)); }
        catch (IOException e) { throw new UncheckedIOException("파일을 읽지 못했습니다.", e); }
    }

    @Override
    public void delete(String key) {
        if (!StringUtils.hasText(key)) return;
        try { Files.deleteIfExists(resolve(key)); } catch (Exception ignored) { }
    }
}
