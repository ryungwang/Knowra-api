package com.knowra.series;

import com.knowra.post.entity.PostDTO;
import com.knowra.post.service.PostQuery;
import com.querydsl.core.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.knowra.post.service.PostQuery.POST;

/**
 * 연재 — 글쓴이 한 사람의 글 묶음. 만들기·고치기·지우기는 주인만, 읽기는 누구나.
 *
 * <p>목록은 보는 사람이 볼 수 있는 글만 낸다 — 비공개 커뮤니티 회차는 멤버가 아니면 빠진다.
 * 그래서 「3화」라는 번호도 보는 사람마다 다를 수 있다. 번호를 저장하지 않고 그때그때 세는 이유다.
 * 익명 커뮤니티로 바뀐 커뮤니티의 회차도 뺀다(넣을 때 막지만, 커뮤니티 설정은 나중에 바뀐다).
 */
@Service
@RequiredArgsConstructor
public class SeriesService {

    public static final int MAX_PER_USER = 30;
    private final JdbcTemplate jdbc;
    private final PostQuery postQuery;

    public List<Map<String, Object>> mine(long me) {
        return jdbc.queryForList("""
                SELECT s.series_sn AS "seriesSn", s.title, s.dscrptn AS "desc",
                       (SELECT COUNT(*) FROM knowra_post.tbl_post p WHERE p.series_sn = s.series_sn AND p.actvtn_yn = 'Y') AS count
                FROM knowra_post.tbl_series s WHERE s.user_sn = ? ORDER BY s.series_sn DESC""", me);
    }

    public long create(long me, String title, String desc) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_post.tbl_series WHERE user_sn = ?", Long.class, me);
        if (n != null && n >= MAX_PER_USER) throw new IllegalArgumentException("연재는 " + MAX_PER_USER + "개까지 만들 수 있어요.");
        return jdbc.queryForObject("INSERT INTO knowra_post.tbl_series (user_sn, title, dscrptn) VALUES (?, ?, ?) RETURNING series_sn",
                Long.class, me, title(title), desc(desc));
    }

    public void update(long me, long seriesSn, String title, String desc) {
        own(me, seriesSn);
        jdbc.update("UPDATE knowra_post.tbl_series SET title = ?, dscrptn = ? WHERE series_sn = ?", title(title), desc(desc), seriesSn);
    }

    /** 연재만 지운다 — 글은 그대로 두고 연재 표시만 뗀다 */
    @Transactional
    public void remove(long me, long seriesSn) {
        own(me, seriesSn);
        jdbc.update("UPDATE knowra_post.tbl_post SET series_sn = NULL WHERE series_sn = ?", seriesSn);
        jdbc.update("DELETE FROM knowra_post.tbl_series WHERE series_sn = ?", seriesSn);
    }

    /**
     * 글에 달 연재 — 비었으면 null(연재에서 빼기). 남의 연재·익명 커뮤니티 글은 거절한다.
     * owner 는 글쓴이다 — 운영진이 남의 글을 고칠 때는 이 함수를 부르지 않는다.
     */
    public Long validFor(Object raw, long owner, Long commSn) {
        if (raw == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))) return null;
        long sn;
        try { sn = Long.parseLong(String.valueOf(raw)); } catch (NumberFormatException e) { return null; }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_post.tbl_series WHERE series_sn = ? AND user_sn = ?", Long.class, sn, owner);
        if (n == null || n == 0) throw new IllegalArgumentException("내 연재에만 넣을 수 있어요.");
        if (postQuery.isAnonymousComm(commSn)) throw new IllegalArgumentException("익명 커뮤니티 글은 연재에 넣을 수 없어요.");
        return sn;
    }

    /** 연재 한 편 — 정보 + 보는 사람이 볼 수 있는 회차(올린 순서) */
    public Map<String, Object> get(long seriesSn, Long viewer) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.series_sn AS "seriesSn", s.title, s.dscrptn AS "desc", s.user_sn AS "userSn",
                       u.nick_name AS "nickName", u.name AS "authorNm"
                FROM knowra_post.tbl_series s JOIN knowra_user.tbl_user u ON u.user_sn = s.user_sn
                WHERE s.series_sn = ?""", seriesSn);
        if (rows.isEmpty()) throw new IllegalArgumentException("없는 연재예요.");
        Map<String, Object> out = new LinkedHashMap<>(rows.get(0));
        out.put("mine", viewer != null && viewer == ((Number) out.get("userSn")).longValue());

        List<Tuple> tuples = postQuery.base()
                .where(POST.seriesSn.eq(seriesSn), PostQuery.alive(), PostQuery.visibleTo(viewer))
                .orderBy(POST.frstCrtDt.asc(), POST.postSn.asc())
                .fetch();
        List<PostDTO> posts = postQuery.decorate(tuples, viewer).stream().filter(p -> !p.isAnonymous()).toList();
        out.put("posts", posts);
        return out;
    }

    private void own(long me, long seriesSn) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_post.tbl_series WHERE series_sn = ? AND user_sn = ?", Long.class, seriesSn, me);
        if (n == null || n == 0) throw new IllegalArgumentException("내 연재만 고칠 수 있어요.");
    }

    private static String title(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) throw new IllegalArgumentException("연재 제목을 적어 주세요.");
        if (t.length() > 60) throw new IllegalArgumentException("연재 제목은 60자까지예요.");
        return t;
    }

    private static String desc(String raw) {
        String d = raw == null ? "" : raw.trim();
        if (d.length() > 200) throw new IllegalArgumentException("소개는 200자까지예요.");
        return d.isEmpty() ? null : d;
    }
}
