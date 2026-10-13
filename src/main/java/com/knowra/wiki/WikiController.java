package com.knowra.wiki;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 커뮤니티 규칙·위키. 읽기 GET 은 비로그인도(공개 커뮤니티), 쓰기 POST 는 운영진. */
@RestController
@RequestMapping("/api/community/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiService wiki;

    /** 규칙 + 위키 목차 한 번에 — 커뮤니티 화면의 「소개」 칸 */
    @GetMapping("/overview")
    public ResultVO overview(@RequestParam long commSn, @CurrentUser Long me) {
        return run(() -> { wiki.requireReadable(commSn, me); return Map.of("rules", wiki.rules(commSn), "pages", wiki.pages(commSn)); });
    }

    @GetMapping("/page")
    public ResultVO page(@RequestParam long commSn, @RequestParam String slug, @CurrentUser Long me) {
        return run(() -> { wiki.requireReadable(commSn, me); return Map.of("page", wiki.page(commSn, slug)); });
    }

    /** body: commSn, rules[{title, body}] */
    @PostMapping("/rules/save")
    public ResultVO saveRules(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(() -> Map.of("rules", wiki.saveRules(Long.parseLong(String.valueOf(b.get("commSn"))), (List<?>) b.get("rules"), me)));
    }

    /** body: commSn, slug?(없으면 새 페이지), title, content */
    @PostMapping("/page/save")
    public ResultVO savePage(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(() -> Map.of("page", wiki.savePage(Long.parseLong(String.valueOf(b.get("commSn"))),
                b.get("slug") == null ? null : String.valueOf(b.get("slug")), String.valueOf(b.getOrDefault("title", "")),
                b.get("content") == null ? "" : String.valueOf(b.get("content")), me)));
    }

    /** body: commSn, slug */
    @PostMapping("/page/delete")
    public ResultVO deletePage(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(() -> { wiki.deletePage(Long.parseLong(String.valueOf(b.get("commSn"))), String.valueOf(b.get("slug")), me); return Map.of("deleted", true); });
    }

    private ResultVO run(java.util.function.Supplier<Map<String, Object>> f) {
        ResultVO r = new ResultVO();
        try {
            f.get().forEach(r::putResult);
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        }
        return r;
    }
}
