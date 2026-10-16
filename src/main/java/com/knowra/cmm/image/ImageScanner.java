package com.knowra.cmm.image;

/**
 * 올리는 사진을 알려진 불법 이미지(아동 성착취물 등) 해시 목록과 대조하는 자리.
 *
 * <p>⚠️ 지금은 대조하지 않는다({@link NoopImageScanner}). 해시 목록은 공개되지 않아 승인받은 서비스를 거쳐야 한다:
 * Microsoft PhotoDNA(신청·승인), Thorn Safer(계약), 또는 Cloudflare 를 쓴다면 CSAM Scanning Tool(대시보드에서 켜면 CDN 단에서 막는다 —
 * 코드가 필요 없다). 붙일 때는 이 인터페이스를 구현한 빈을 하나 더 만들고 {@code knowra.image-scan.provider} 로 고른다.
 *
 * <p>FileUtil 이 위치정보를 지운 뒤, 저장하기 <b>전에</b> 부른다 — 걸리면 저장하지 않는다.
 */
public interface ImageScanner {

    enum Verdict { OK, MATCH }

    Verdict scan(byte[] image, String ext);
}
