package com.knowra.tag;

import com.knowra.post.entity.PostDTO;
import com.knowra.post.service.FeedCacheService;
import com.knowra.post.service.PostService;
import com.knowra.user.service.InterestScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 태그 팔로우와 태그 화면(/tag/:이름).
 * 팔로우하면 「팔로잉」 피드에 그 태그의 글이 들어오고(FeedService), 추천 피드의 관심 점수도 한 번 올려 둔다.
 */
@Service
@RequiredArgsConstructor
public class TagFollowService {

    /** 한 사람이 팔로우할 수 있는 태그 수 — 팔로잉 피드가 사실상 「최신」이 되지 않게 */
    static final int MAX_FOLLOWS = 100;
    static final int PAGE = 20;
    /** 팔로우할 때 관심 점수에 더하는 값 — 글을 읽거나 좋아요한 것보다 크게(직접 고른 관심이다) */
    static final double FOLLOW_INTEREST = 10;

    private final JdbcTemplate jdbc;
    private final PostService postService;
    private final InterestScoreService interestScoreService;
    private final FeedCacheService feedCacheService;

    private Long tagSnOf(String name) {
        String n = name == null ? "" : name.trim().replaceFirst("^#", "");
        if (n.isEmpty()) return null;
        // 태그 이름은 「#개발」처럼 # 를 붙여 저장돼 있다(TagService). 주소에는 # 를 뺀 이름이 온다 — 둘 다 받는다
        return jdbc.query("SELECT tag_sn FROM knowra_com.tbl_tag WHERE tag_nm IN (?, ?) AND actvtn_yn = 'Y' ORDER BY tag_nm DESC LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, "#" + n, n);
    }

    /** 태그 화면 머리 — 누구나 */
    public Map<String, Object> info(String name, Long me) {
        Long tagSn = tagSnOf(name);
        if (tagSn == null) throw new IllegalArgumentException("없는 태그예요.");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tagSn", tagSn);
        out.put("tagNm", jdbc.queryForObject("SELECT tag_nm FROM knowra_com.tbl_tag WHERE tag_sn = ?", String.class, tagSn));
        out.put("postCnt", jdbc.queryForObject("SELECT COUNT(*) FROM knowra_post.tbl_post_tag pt JOIN knowra_post.tbl_post p ON p.post_sn = pt.post_sn WHERE pt.tag_sn = ? AND p.actvtn_yn = 'Y' AND p.stat = 'ACTIVE'", Long.class, tagSn));
        out.put("followerCnt", jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_tag_follow WHERE tag_sn = ?", Long.class, tagSn));
        out.put("following", me != null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM knowra_user.tbl_tag_follow WHERE user_sn = ? AND tag_sn = ?)", Boolean.class, me, tagSn)));
        return out;
    }

    /** 태그의 글 — 최신순, cursor = 마지막 글의 작성 시각 */
    public Map<String, Object> posts(String name, String cursor, Long me) {
        Long tagSn = tagSnOf(name);
        if (tagSn == null) throw new IllegalArgumentException("없는 태그예요.");
        LocalDateTime c = cursor == null || cursor.isBlank() ? null : LocalDateTime.parse(cursor);
        List<PostDTO> list = postService.fetchPostsByTag(tagSn, me, c, PAGE + 1);
        boolean more = list.size() > PAGE;
        if (more) list = list.subList(0, PAGE);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("nextCursor", more ? list.get(list.size() - 1).getFrstCrtDt().toString() : null);
        return out;
    }

    /** 팔로우·풀기. params: name, follow */
    @Transactional
    public Map<String, Object> follow(Map<String, Object> params, long me) {
        Long tagSn = tagSnOf(String.valueOf(params.get("name")));
        if (tagSn == null) throw new IllegalArgumentException("없는 태그예요.");
        boolean on = Boolean.parseBoolean(String.valueOf(params.get("follow")));
        if (on) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_tag_follow WHERE user_sn = ?", Integer.class, me);
            if (n != null && n >= MAX_FOLLOWS) throw new IllegalArgumentException(com.knowra.cmm.i18n.Msg.t("태그는 {0}개까지 팔로우할 수 있어요.", MAX_FOLLOWS));
            int added = jdbc.update("INSERT INTO knowra_user.tbl_tag_follow (user_sn, tag_sn) VALUES (?, ?) ON CONFLICT DO NOTHING", me, tagSn);
            if (added > 0) interestScoreService.updateForTags(me, List.of(tagSn), FOLLOW_INTEREST);
        } else {
            jdbc.update("DELETE FROM knowra_user.tbl_tag_follow WHERE user_sn = ? AND tag_sn = ?", me, tagSn);
        }
        // 팔로잉·추천 피드는 사람마다 캐시돼 있다 — 바로 반영되게 비운다
        feedCacheService.invalidateUserFeeds(me);
        return Map.of("following", on);
    }

    /** 내가 팔로우한 태그 — 최근에 팔로우한 순 */
    public Map<String, Object> following(long me) {
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT t.tag_sn AS "tagSn", t.tag_nm AS "tagNm", f.created_at AS "followedAt"
                FROM knowra_user.tbl_tag_follow f JOIN knowra_com.tbl_tag t ON t.tag_sn = f.tag_sn
                WHERE f.user_sn = ? ORDER BY f.created_at DESC""", me);
        return Map.of("list", list);
    }

    /** 피드용 — 팔로우한 태그 번호 */
    public List<Long> followedTagSns(long me) {
        return jdbc.queryForList("SELECT tag_sn FROM knowra_user.tbl_tag_follow WHERE user_sn = ?", Long.class, me);
    }
}
