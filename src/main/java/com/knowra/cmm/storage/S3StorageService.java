package com.knowra.cmm.storage;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * prod 저장소 — synub 공유 S3 버킷. 자격증명은 기본 체인(인스턴스 롤·표준 AWS_* env), 버킷·리전은 env 로만
 * (기본값 없음 — 빠뜨리면 기동이 실패해야 한다). 공개 파일은 {@code public/} 아래, 버킷 정책이 거기만 읽기 허용.
 */
@Service
@Profile("prod")
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final StorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageService(StorageProperties properties) {
        this.properties = properties;
        StorageProperties.S3 s3 = properties.getS3();
        if (!StringUtils.hasText(s3.getBucket()) || !StringUtils.hasText(s3.getRegion()))
            throw new IllegalStateException("app.storage.s3.bucket·region(AWS_S3_BUCKET·AWS_REGION)이 prod 에 필요합니다.");
        Region region = Region.of(s3.getRegion());
        this.client = S3Client.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
        this.presigner = S3Presigner.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    @Override
    public PublicFile storePublic(byte[] content, String path, String originalFilename) {
        String key = StorageKeys.publicKey(properties.getKeyPrefix(), path, originalFilename);
        String ct = StorageKeys.safeContentType(key);
        client.putObject(PutObjectRequest.builder()
                .bucket(properties.getS3().getBucket()).key(key).contentType(ct)
                // 바뀌면 키(uuid)가 새로 생기므로 길게 캐시해도 안전하다
                .cacheControl("public, max-age=31536000, immutable")
                .build(), RequestBody.fromBytes(content));
        return new PublicFile(key, publicUrl(key), ct, content.length);
    }

    @Override
    public String publicUrl(String key) {
        String base = properties.getS3().getPublicBaseUrl();
        if (StringUtils.hasText(base)) return base.replaceAll("/+$", "") + "/" + key;
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(properties.getS3().getBucket(), properties.getS3().getRegion(), key);
    }

    @Override
    public String keyFromUrl(String url) {
        if (url == null) return null;
        String base = properties.getS3().getPublicBaseUrl();
        String[] bases = {
                StringUtils.hasText(base) ? base.replaceAll("/+$", "") + "/" : null,
                "https://%s.s3.%s.amazonaws.com/".formatted(properties.getS3().getBucket(), properties.getS3().getRegion())
        };
        for (String b : bases) if (b != null && url.startsWith(b)) return url.substring(b.length());
        return null;
    }

    @Override
    public String presignedUrl(String key, Duration ttl) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(properties.getS3().getBucket()).key(key).build())
                .build()).url().toString();
    }

    @Override
    public byte[] download(String key) {
        return client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.getS3().getBucket()).key(key).build()).asByteArray();
    }

    @Override
    public void delete(String key) {
        if (!StringUtils.hasText(key)) return;
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getS3().getBucket()).key(key).build());
        } catch (RuntimeException ex) {
            log.warn("S3 객체 삭제 실패 {} — 고아로 남음", key, ex);
        }
    }

    @PreDestroy
    void close() {
        client.close();
        presigner.close();
    }
}
