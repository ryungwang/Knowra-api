package com.knowra.link;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** 링크 미리보기. 비로그인도 쓴다 — 공개 글을 보는 사람도 카드가 보여야 한다. */
@RestController
@RequiredArgsConstructor
public class LinkController {

    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final int MAX_IMAGE = 2 * 1024 * 1024;
    private final LinkPreviewService service;

    /** GET /api/link/preview?url= → { url, title, description, siteName, image(프록시 경로) } 또는 preview=null */
    @GetMapping("/api/link/preview")
    public ResultVO preview(@RequestParam String url) {
        ResultVO r = new ResultVO();
        Map<String, Object> p = url.length() > 2000 ? null : service.preview(url);
        if (p != null && p.get("image") != null) {
            p = new java.util.LinkedHashMap<>(p);
            p.put("image", "/api/link/image?u=" + URLEncoder.encode(String.valueOf(p.get("image")), StandardCharsets.UTF_8));
        }
        r.putResult("preview", p);
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /**
     * 미리보기 이미지 프록시. 같은 SSRF 검사, 2MB·이미지 형식만. svg 는 안 받는다(스크립트를 품을 수 있다).
     * 브라우저 캐시 하루 — 같은 카드를 다시 볼 때 우리 서버도 안 거친다.
     */
    @GetMapping("/api/link/image")
    public ResponseEntity<byte[]> image(@RequestParam("u") String u) {
        try {
            SafeFetcher.Result res = SafeFetcher.get(u, "image/*", MAX_IMAGE + 1);
            String type = res.contentType().split(";")[0].trim().toLowerCase();
            if (res.status() != 200 || !IMAGE_TYPES.contains(type) || res.body().length > MAX_IMAGE) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(type))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(res.body());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
