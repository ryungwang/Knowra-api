package com.knowra.cmm.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 대조하지 않는다 — 해시 대조 서비스를 붙이기 전 기본값(ImageScanner 주석 참고) */
@Component
@ConditionalOnProperty(name = "knowra.image-scan.provider", havingValue = "none", matchIfMissing = true)
public class NoopImageScanner implements ImageScanner {
    @Override
    public Verdict scan(byte[] image, String ext) { return Verdict.OK; }
}
