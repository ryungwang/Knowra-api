package com.knowra.cmm.storage;

import java.time.Duration;

/**
 * 파일 저장소. 프로필로 갈린다 — prod = AWS S3({@link S3StorageService}), 그 밖(local·dev) = 로컬 디스크
 * ({@link LocalDiskStorageService}). 특정 SDK 에 직접 의존하지 않는다(전역 규칙 「파일 스토리지 추상화」,
 * 레퍼런스 synub-office {@code infra/storage}).
 *
 * <p>Knowra 의 업로드(글 본문 이미지·댓글/DM 사진·프로필·커뮤니티 로고/배너)는 <b>전부 공개 콘텐츠</b>라
 * {@link #PUBLIC_PREFIX} 아래에 두고 서명 없는 영구 URL 로 보여 준다. 글 HTML 에 주소가 박히므로
 * 만료되는 presigned URL 은 쓸 수 없다. 키에 uuid 가 들어가 추측할 수 없다.
 * ⚠️ 비공개여야 하는 문서가 생기면 여기 말고 presigned 경로를 새로 연다.
 */
public interface StorageService {

    /** 공개 객체 키 prefix. 버킷 정책이 이 경로에만 s3:GetObject 를 허용한다(synub 공유 버킷 규칙). */
    String PUBLIC_PREFIX = "public";

    Duration DEFAULT_URL_TTL = Duration.ofMinutes(5);

    /**
     * 공개 저장. {@code path} 는 prefix 를 뺀 경로로 <b>이름이 아니라 ID</b>로 짓는다(예: {@code users/12/profile}).
     * 키는 {@code public/{keyPrefix}/{path}/{uuid}.{ext}}.
     */
    PublicFile storePublic(byte[] content, String path, String originalFilename);

    /** 키 → 서명 없는 영구 URL */
    String publicUrl(String key);

    /** 이 저장소가 만든 공개 URL 에서 키를 되찾는다. 우리 URL 이 아니면 null. */
    String keyFromUrl(String url);

    /** 단기 서명 URL(비공개 객체용). 지금 Knowra 는 쓰지 않지만 규칙상 인터페이스를 맞춘다. */
    String presignedUrl(String key, Duration ttl);

    byte[] download(String key);

    /** 실패해도 예외를 던지지 않는다(고아 객체는 남을 수 있다). */
    void delete(String key);

    record PublicFile(String key, String url, String contentType, long size) { }
}
