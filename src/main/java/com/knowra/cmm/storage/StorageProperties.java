package com.knowra.cmm.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.storage.*} — synub-office 와 같은 이름. local·dev 는 {@code local.*}, prod 는 {@code s3.*}.
 * {@code keyPrefix} 는 공유 버킷에서 앱을 가르는 최상위 폴더({@code AWS_S3_PREFIX}).
 */
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {

    private String keyPrefix = "knowra";
    private final Local local = new Local();
    private final S3 s3 = new S3();

    @Getter
    @Setter
    public static class Local {
        /** 저장 폴더 */
        private String dir = "./storage-data";
        /** 이 API 의 바깥 주소 — 파일은 {publicBaseUrl}/api/files/{key} 로 열린다 */
        private String publicBaseUrl = "http://localhost:8081";
    }

    @Getter
    @Setter
    public static class S3 {
        private String bucket;
        private String region;
        /** CDN·커스텀 도메인. 비우면 버킷 기본 주소 */
        private String publicBaseUrl;
    }
}
