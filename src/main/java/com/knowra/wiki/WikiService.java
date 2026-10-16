package com.knowra.wiki;

import com.knowra.cmm.util.HtmlSanitizer;
import com.knowra.community.entity.TblComm;
import com.knowra.community.entity.TblCommMbr;
import com.knowra.community.repository.TblCommMbrRepository;
import com.knowra.community.repository.TblCommRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 커뮤니티 규칙·위키. 읽기: 공개 커뮤니티는 누구나, 비공개는 활성 멤버만. 쓰기: 운영진(OWNER·ADMIN)만.
 * 위키 본문은 저장 전에 HtmlSanitizer — 글과 같은 XSS 규칙.
 */
@Service
@RequiredArgsConstructor
public class WikiService {

    public static final int MAX_RULES = 10, MAX_PAGES = 20, MAX_CONTENT = 100_000;
    private final CommRuleRepository rules;
    private final CommPageRepository pages;
    private final TblCommRepository comms;
    private final TblCommMbrRepository mbrs;
    private final com.knowra.community.modlog.ModLogService modLog;

    public TblComm requireReadable(long commSn, Long me) {
        TblComm c = comms.findByCommSn(commSn);
        if (c == null || !"Y".equals(c.getStat())) throw new IllegalArgumentException("커뮤니티를 찾을 수 없어요.");
        if ("private".equals(c.getPrvcyStng()) && activeRole(commSn, me) == null) throw new IllegalArgumentException("멤버만 볼 수 있어요.");
        return c;
    }

    private String activeRole(long commSn, Long me) {
        if (me == null) return null;
        return mbrs.findByCommSnAndUserSn(commSn, me).filter(m -> "ACTIVE".equals(m.getStat()) && "Y".equals(m.getActvtnYn()))
                .map(TblCommMbr::getRole).orElse(null);
    }

    private void requireManager(long commSn, long me) {
        String r = activeRole(commSn, me);
        if (!"OWNER".equals(r) && !"ADMIN".equals(r)) throw new IllegalArgumentException("커뮤니티 운영진만 고칠 수 있어요.");
    }

    public List<Map<String, Object>> rules(long commSn) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TblCommRule r : rules.findAllByCommSnOrderByOrdAsc(commSn)) out.add(Map.of("ord", r.getOrd(), "title", r.getTitle(), "body", r.getBody()));
        return out;
    }

    public boolean hasRules(long commSn) { return rules.countByCommSn(commSn) > 0; }

    /** 규칙 통째로 바꾸기. items: [{title, body}] — 빈 제목은 건너뛴다. */
    @Transactional
    public List<Map<String, Object>> saveRules(long commSn, List<?> items, long me) {
        requireManager(commSn, me);
        List<TblCommRule> next = new ArrayList<>();
        int ord = 0;
        for (Object o : items == null ? List.of() : items) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String t = str(m.get("title")).trim(), b = str(m.get("body")).trim();
            if (t.isEmpty()) continue;
            if (t.length() > 60 || b.length() > 300) throw new IllegalArgumentException("규칙 제목은 60자, 설명은 300자까지예요.");
            next.add(TblCommRule.builder().commSn(commSn).ord(ord++).title(t).body(b).build());
        }
        if (next.size() > MAX_RULES) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("규칙은 {0}개까지예요.", MAX_RULES));
        rules.deleteAllOf(commSn);
        rules.saveAll(next);
        modLog.record(commSn, me, com.knowra.community.modlog.ModLogService.RULES, null, null, com.knowra.community.modlog.ModLogDetail.count(next.size()));
        return rules(commSn);
    }

    public List<Map<String, Object>> pages(long commSn) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TblCommPage p : pages.findAllByCommSnOrderByOrdAscPageSnAsc(commSn)) {
            out.add(Map.of("slug", p.getSlug(), "title", p.getTitle(), "updatedAt", p.getUpdatedAt()));
        }
        return out;
    }

    public Map<String, Object> page(long commSn, String slug) {
        TblCommPage p = pages.findByCommSnAndSlug(commSn, slug).orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없어요."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slug", p.getSlug()); m.put("title", p.getTitle()); m.put("content", p.getContent()); m.put("updatedAt", p.getUpdatedAt());
        return m;
    }

    /** slug 가 있으면 고치고, 없으면 제목으로 새 slug 를 만들어 새 페이지. */
    @Transactional
    public Map<String, Object> savePage(long commSn, String slug, String title, String content, long me) {
        requireManager(commSn, me);
        String t = title == null ? "" : title.trim();
        if (t.isEmpty() || t.length() > 80) throw new IllegalArgumentException("페이지 제목을 1~80자로 써 주세요.");
        if (content != null && content.length() > MAX_CONTENT) throw new IllegalArgumentException("본문이 너무 길어요.");
        TblCommPage p;
        if (slug != null && !slug.isBlank()) {
            p = pages.findByCommSnAndSlug(commSn, slug).orElseThrow(() -> new IllegalArgumentException("페이지를 찾을 수 없어요."));
        } else {
            if (pages.countByCommSn(commSn) >= MAX_PAGES) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("위키 페이지는 {0}개까지예요.", MAX_PAGES));
            String base = slugOf(t), s = base;
            for (int i = 2; pages.findByCommSnAndSlug(commSn, s).isPresent(); i++) s = base + "-" + i;
            p = TblCommPage.builder().commSn(commSn).slug(s).ord((int) pages.countByCommSn(commSn)).build();
        }
        p.setTitle(t);
        p.setContent(HtmlSanitizer.clean(content == null ? "" : content));
        p.setUpdatedBy(me);
        p.setUpdatedAt(LocalDateTime.now());
        pages.save(p);
        modLog.record(commSn, me, com.knowra.community.modlog.ModLogService.WIKI_SAVE, null, null, t);
        return page(commSn, p.getSlug());
    }

    @Transactional
    public void deletePage(long commSn, String slug, long me) {
        requireManager(commSn, me);
        pages.findByCommSnAndSlug(commSn, slug).ifPresent(p -> {
            pages.delete(p);
            modLog.record(commSn, me, com.knowra.community.modlog.ModLogService.WIKI_DELETE, null, null, p.getTitle());
        });
    }

    /** 주소용 이름 — 한글 제목도 살린다(영문·숫자·한글·하이픈). 비면 page. */
    static String slugOf(String title) {
        String s = Normalizer.normalize(title, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-z가-힣]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 36) s = s.substring(0, 36).replaceAll("-+$", "");
        return s.isEmpty() ? "page" : s;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
