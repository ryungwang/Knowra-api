package com.knowra.draft;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 임시 저장함 — 내 것만 보고 고치고 지운다(남의 draftSn 은 없는 것과 같다).
 * 본문 200KB·제목 300자·사람당 20개. 꽉 차면 새로 만들지 않고 알려 준다(조용히 지우지 않는다).
 */
@RestController
@RequestMapping("/api/draft")
@RequiredArgsConstructor
public class DraftController {

    public static final int MAX_DRAFTS = 20, MAX_CONTENT = 200_000, MAX_TITLE = 300;
    private final TblPostDraftRepository repo;

    @PostMapping("/list")
    public ResultVO list(@CurrentUser Long me) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TblPostDraft d : repo.findAllByUserSnOrderByUpdatedAtDesc(me)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("draftSn", d.getDraftSn()); m.put("commSn", d.getCommSn()); m.put("title", d.getTitle());
            m.put("content", d.getContent());
            m.put("tags", d.getTags().isBlank() ? List.of() : List.of(d.getTags().split(",")));
            m.put("updatedAt", d.getUpdatedAt());
            list.add(m);
        }
        return ok("list", list);
    }

    /** body: draftSn?(있으면 덮어쓰기), title, content, tags[], commSn? */
    @PostMapping("/save")
    @SuppressWarnings("unchecked")
    public ResultVO save(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        String title = String.valueOf(b.getOrDefault("title", "")).trim();
        String content = String.valueOf(b.getOrDefault("content", ""));
        if (title.length() > MAX_TITLE) return fail("제목은 " + MAX_TITLE + "자까지예요.");
        if (content.length() > MAX_CONTENT) return fail("본문이 너무 길어 임시 저장할 수 없어요.");
        if (title.isEmpty() && content.replaceAll("<[^>]+>", "").isBlank()) return fail("비어 있는 글은 저장하지 않아요.");
        List<String> tags = b.get("tags") instanceof List<?> l ? l.stream().map(String::valueOf).map(String::trim)
                .filter(t -> !t.isEmpty() && !t.contains(",")).limit(20).toList() : List.of();
        Long commSn = b.get("commSn") == null ? null : Long.valueOf(String.valueOf(b.get("commSn")));

        TblPostDraft d;
        if (b.get("draftSn") != null) {
            d = repo.findById(Long.valueOf(String.valueOf(b.get("draftSn")))).filter(x -> x.getUserSn() == me).orElse(null);
            if (d == null) return fail("임시 저장한 글을 찾을 수 없어요.");
        } else {
            if (repo.countByUserSn(me) >= MAX_DRAFTS) return fail("임시 저장함이 가득 찼어요(" + MAX_DRAFTS + "개). 안 쓰는 글을 지워 주세요.");
            d = TblPostDraft.builder().userSn(me).build();
        }
        d.setTitle(title); d.setContent(com.knowra.cmm.util.HtmlSanitizer.clean(content)); d.setTags(String.join(",", tags)); d.setCommSn(commSn);
        d.setUpdatedAt(LocalDateTime.now());
        repo.save(d);
        ResultVO r = ok("draftSn", d.getDraftSn());
        r.putResult("count", repo.countByUserSn(me));
        return r;
    }

    /** body: draftSn */
    @PostMapping("/delete")
    public ResultVO delete(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        if (b.get("draftSn") != null) {
            repo.findById(Long.valueOf(String.valueOf(b.get("draftSn")))).filter(x -> x.getUserSn() == me).ifPresent(repo::delete);
        }
        return ok("count", repo.countByUserSn(me));
    }

    private static ResultVO ok(String k, Object v) { ResultVO r = new ResultVO(); r.putResult(k, v); r.setResultCode(ResponseCode.SUCCESS.getCode()); return r; }
    private static ResultVO fail(String msg) { ResultVO r = new ResultVO(); r.setResultCode(ResponseCode.SAVE_ERROR.getCode()); r.setResultMessage(msg); return r; }
}
