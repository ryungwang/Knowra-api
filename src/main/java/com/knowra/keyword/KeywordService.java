package com.knowra.keyword;

import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.post.entity.QTblPost;
import com.knowra.post.entity.TblPost;
import com.knowra.post.service.PostQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 키워드 알림 — 새 글이 「세상에 나올 때」(바로 올림·예약 글이 올라갈 때) 한 번 훑는다.
 * 한 글에 키워드가 여럿 맞아도 사람마다 알림 하나. 볼 수 없는 글·차단·숨긴 사람·내 글은 빼고,
 * 그 커뮤니티 「새 글 알림」을 켠 멤버도 뺀다(같은 글로 알림이 두 번 간다).
 */
@Service
@RequiredArgsConstructor
public class KeywordService {

    public static final int MAX = 10, MAX_TARGETS = 500;
    private final JdbcTemplate jdbc;
    private final PostQuery postQuery;
    private final ApplicationEventPublisher events;

    public List<Map<String, Object>> list(long me) {
        return jdbc.queryForList("SELECT keyword, created_at AS \"createdAt\" FROM knowra_user.tbl_keyword_watch WHERE user_sn = ? ORDER BY created_at", me);
    }

    public void add(long me, String raw) {
        String k = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (k.length() < 2 || k.length() > 30) throw new IllegalArgumentException("키워드는 2~30자로 넣어 주세요.");
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_keyword_watch WHERE user_sn = ?", Long.class, me);
        if (n != null && n >= MAX) throw new IllegalArgumentException("키워드는 " + MAX + "개까지예요.");
        int added = jdbc.update("INSERT INTO knowra_user.tbl_keyword_watch (user_sn, keyword, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", me, k, LocalDateTime.now());
        if (added == 0) throw new IllegalArgumentException("이미 있는 키워드예요.");
    }

    public void remove(long me, String keyword) {
        jdbc.update("DELETE FROM knowra_user.tbl_keyword_watch WHERE user_sn = ? AND lower(keyword) = lower(?)", me, keyword == null ? "" : keyword.strip());
    }

    public void onPublished(TblPost post) {
        String plain = org.jsoup.Jsoup.parse(post.getPostCntnt() == null ? "" : post.getPostCntnt()).text();
        String text = (post.getPostTtl() + " " + (plain.length() > 20000 ? plain.substring(0, 20000) : plain));
        List<Map<String, Object>> hits = jdbc.queryForList("""
                SELECT w.user_sn, MIN(w.keyword) AS keyword FROM knowra_user.tbl_keyword_watch w
                WHERE w.user_sn <> ? AND position(lower(w.keyword) IN lower(?)) > 0
                  AND EXISTS (SELECT 1 FROM knowra_user.tbl_user u WHERE u.user_sn = w.user_sn AND u.status = 'Y' AND u.actvtn_yn = 'Y')
                  AND NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_mbr m
                                  WHERE m.comm_sn = ? AND m.user_sn = w.user_sn AND m.stat = 'ACTIVE' AND m.notif_yn = 'Y')
                GROUP BY w.user_sn LIMIT ?""", post.getUserSn(), text, post.getCommSn(), MAX_TARGETS);
        QTblPost P = QTblPost.tblPost;
        for (Map<String, Object> h : hits) {
            long u = ((Number) h.get("user_sn")).longValue();
            // 그 사람이 볼 수 있는 글인가(비공개 커뮤니티·차단) + 그 사람이 숨긴 작성자가 아닌가
            boolean canSee = postQuery.base().where(P.postSn.eq(post.getPostSn()), PostQuery.alive(), PostQuery.visibleTo(u), PostQuery.notMuted(u)).fetchFirst() != null;
            if (!canSee) continue;
            events.publishEvent(new NotifTriggerEvent(u, post.getUserSn(), "KEYWORD", post.getPostSn(), "POST", h.get("keyword") + "|" + post.getPostTtl()));
        }
    }
}
