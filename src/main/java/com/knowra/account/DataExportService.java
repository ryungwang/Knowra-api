package com.knowra.account;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 내 데이터 내려받기 — 개인정보 열람 요청에 사람 손 없이 답하는 수단.
 *
 * <p>담는 것은 「내가 넣은 것」이다. 받은 DM 은 보낸 사람의 글이라 빼고, 보낸 DM 만 담는다.
 * 비밀번호 해시·소셜 제공자 ID·로그인 실패 수 같은 내부 값은 담지 않는다 — 파일이 새어도 계정을 열 단서가 없게.
 */
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<String, Object> export(long me) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", LocalDateTime.now().toString());
        out.put("service", "knowra");

        out.put("profile", jdbc.queryForMap("""
                SELECT login_id AS "loginId", email, phone, name, nick_name AS "nickName", bio, interest,
                       sns_name AS "socialProvider", join_ymd AS "joinedAt"
                FROM knowra_user.tbl_user WHERE user_sn = ?""", me));
        out.put("settings", first(jdbc.queryForList("""
                SELECT theme_typ AS theme, cmt_ntfctn_yn AS "commentNotify", like_ntfctn_yn AS "likeNotify",
                       flwr_ntfctn_yn AS "followNotify", sys_ntfctn_yn AS "communityNotify", digest_yn AS "weeklyDigest"
                FROM knowra_user.tbl_user_stng WHERE user_sn = ?""", me)));

        out.put("posts", jdbc.queryForList("""
                SELECT p.post_sn AS "postSn", c.comm_dspl_nm AS community, p.series_sn AS "seriesSn", p.post_ttl AS title, p.post_cntnt AS "contentHtml",
                       p.stat AS status, p.like_cnt AS likes, p.cmt_cnt AS comments, p.view_cnt AS views, p.frst_crt_dt AS "createdAt", p.mdfcn_dt AS "updatedAt"
                FROM knowra_post.tbl_post p LEFT JOIN knowra_community.tbl_comm c ON c.comm_sn = p.comm_sn
                WHERE p.user_sn = ? AND p.actvtn_yn = 'Y' ORDER BY p.post_sn""", me));
        out.put("series", jdbc.queryForList(
                "SELECT series_sn AS \"seriesSn\", title, dscrptn AS description, created_at AS \"createdAt\" FROM knowra_post.tbl_series WHERE user_sn = ? ORDER BY series_sn", me));
        out.put("comments", jdbc.queryForList("""
                SELECT c.post_cmt_sn AS "commentSn", c.post_sn AS "postSn", p.post_ttl AS "postTitle", c.cmt_cntnt AS content,
                       c.img_url AS image, c.frst_crt_dt AS "createdAt"
                FROM knowra_post.tbl_post_cmt c LEFT JOIN knowra_post.tbl_post p ON p.post_sn = c.post_sn
                WHERE c.user_sn = ? AND c.actvtn_yn = 'Y' AND c.stat = 'ACTIVE' ORDER BY c.post_cmt_sn""", me));
        out.put("communities", jdbc.queryForList("""
                SELECT c.comm_nm AS slug, c.comm_dspl_nm AS name, m.role, m.stat AS status, m.frst_crt_dt AS "joinedAt"
                FROM knowra_community.tbl_comm_mbr m JOIN knowra_community.tbl_comm c ON c.comm_sn = m.comm_sn
                WHERE m.user_sn = ? ORDER BY m.frst_crt_dt""", me));
        out.put("following", jdbc.queryForList("""
                SELECT u.nick_name AS "nickName", f.frst_crt_dt AS since FROM knowra_user.tbl_user_flwr f
                JOIN knowra_user.tbl_user u ON u.user_sn = f.flwng_user_sn WHERE f.flwr_user_sn = ? AND f.actvtn_yn = 'Y'""", me));
        out.put("followers", jdbc.queryForList("""
                SELECT u.nick_name AS "nickName", f.frst_crt_dt AS since FROM knowra_user.tbl_user_flwr f
                JOIN knowra_user.tbl_user u ON u.user_sn = f.flwr_user_sn WHERE f.flwng_user_sn = ? AND f.actvtn_yn = 'Y'""", me));
        out.put("saved", jdbc.queryForList("""
                SELECT s.post_sn AS "postSn", p.post_ttl AS title, s.frst_crt_dt AS "savedAt" FROM knowra_post.tbl_post_save s
                LEFT JOIN knowra_post.tbl_post p ON p.post_sn = s.post_sn WHERE s.user_sn = ? AND s.actvtn_yn = 'Y'""", me));
        out.put("blocked", jdbc.queryForList("""
                SELECT u.nick_name AS "nickName", b.frst_crt_dt AS since FROM knowra_user.tbl_user_block b
                JOIN knowra_user.tbl_user u ON u.user_sn = b.blocked_sn WHERE b.user_sn = ?""", me));
        out.put("badges", jdbc.queryForList(
                "SELECT badge_key AS badge, earned_at AS \"earnedAt\" FROM knowra_user.tbl_user_badge WHERE user_sn = ? ORDER BY earned_at", me));
        // 보낸 DM 만 — 받은 것은 상대가 쓴 글이다. 대화방은 상대 닉네임으로 알아보게 한다
        out.put("sentMessages", jdbc.queryForList("""
                SELECT m.room_sn AS "roomSn",
                       (SELECT string_agg(u.nick_name, ', ') FROM knowra_user.tbl_chat_room_mbr rm JOIN knowra_user.tbl_user u ON u.user_sn = rm.user_sn
                        WHERE rm.room_sn = m.room_sn AND rm.user_sn <> m.user_sn) AS "with",
                       m.msg_cntnt AS content, m.img_url AS image, m.frst_crt_dt AS "sentAt"
                FROM knowra_user.tbl_chat_msg m WHERE m.user_sn = ? ORDER BY m.msg_sn""", me));
        out.put("recentLogins", jdbc.queryForList(
                "SELECT lgn_dt AS \"at\", lgn_ip AS ip FROM knowra_user.tbl_user_lgn_hstry WHERE user_sn = ? ORDER BY lgn_hstry_sn DESC LIMIT 100", me));
        return out;
    }

    private static Map<String, Object> first(List<Map<String, Object>> rows) { return rows.isEmpty() ? Map.of() : rows.get(0); }
}
