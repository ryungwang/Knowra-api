package com.knowra.cmm.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 엔티티·정적 유틸에서 키 → 공개 URL 을 만들 수 있게 붙잡아 둔다(TblComFile.getUrl 등).
 * 스프링이 뜨기 전(테스트 등)에는 null 을 돌려준다.
 */
@Component
public class StorageUrls {

    private static volatile StorageService storage;
    private final StorageService injected;

    public StorageUrls(StorageService storageService) { this.injected = storageService; }

    @PostConstruct
    void init() { storage = injected; }

    public static String publicUrl(String key) {
        StorageService s = storage;
        return s == null || key == null ? null : s.publicUrl(key);
    }

    /**
     * 조회 결과의 파일 칸들 → 화면 주소. 키가 있으면 저장소 공개 URL, 없으면 예전 {@code /upload/...} 상대 경로.
     * 프로필 사진 등 여러 조회가 같은 규칙을 쓰도록 한 곳에 둔다.
     */
    public static String of(String pathNm, String strgFileNm, String extNm, String fileKey) {
        if (fileKey != null) return publicUrl(fileKey);
        return pathNm == null ? null : pathNm + "/" + strgFileNm + "." + extNm;
    }

    public static String keyFromUrl(String url) {
        StorageService s = storage;
        return s == null ? null : s.keyFromUrl(url);
    }
}
