package com.knowra.user.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.post.service.PostService;
import com.knowra.session.SessionService;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 계정 탈퇴. 화면 약속(「글·댓글·커뮤니티 활동이 모두 지워지고 되돌릴 수 없어요」)대로 실제로 지운다.
 * 예전엔 actvtnYn='N' 표시만 해서 글·이름·이메일이 그대로 남았고, 세션도 살아 있었다.
 *
 * <ul>
 *   <li>글·댓글: PostService 의 삭제를 그대로 탄다(댓글 수·피드 캐시·답글 자리 규칙이 같게).</li>
 *   <li>좋아요·반응·참석·저장·숨김·임시글·팔로우·차단·푸시 구독·알림·관심사 점수: 지운다(좋아요 수는 되돌린다).</li>
 *   <li>커뮤니티: 다른 멤버가 있는 커뮤니티의 주인이면 탈퇴를 막는다(먼저 넘기도록). 혼자인 커뮤니티는 닫는다.</li>
 *   <li>계정 줄은 남기되 개인정보를 지운다 — DM 상대의 대화 기록이 "탈퇴한 사용자"로 보이게. 이메일·아이디·닉네임을
 *       비워 같은 이메일로 다시 가입할 수 있다.</li>
 * </ul>
 * 투표 기록은 남긴다(이미 집계된 결과가 바뀌지 않게, 누구인지는 위에서 지운다).
 *
 * <p><b>14일 유예.</b> 탈퇴를 누르면 바로 지우지 않고 신청 시각만 남긴 뒤 모든 기기에서 로그아웃시킨다
 * ({@link #request}). 그 안에 다시 로그인하면 취소되고({@link #cancel}, 로그인 마무리에서 부른다), 지나면 매일 새벽
 * {@link #purgeDue} 가 위의 방식대로 지운다({@link #purge}). 실수로 눌렀거나 계정을 빼앗긴 사람이 되돌릴 수 있게.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final com.knowra.user.sns.SnsLinkService snsLinks;
    private final TblUserRepository userRepository;
    private final PostService postService;
    private final SessionService sessionService;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final FileUtil fileUtil;
    private final TblComFileRepository comFileRepository;
    private final com.knowra.cmm.messaging.MailService mail;
    private final com.knowra.user.repository.TblUserStngRepository stngs;
    private final org.springframework.transaction.support.TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public static final int GRACE_DAYS = 14;

    /**
     * 탈퇴 신청. params: password(일반 계정) 또는 confirm=아이디(소셜 계정 — 비밀번호가 없다).
     * 확인과 주인 검사는 지금 하고, 지우는 건 {@link #GRACE_DAYS}일 뒤다.
     */
    @Transactional
    public ResultVO request(Map<String, Object> params, long me) {
        ResultVO r = new ResultVO();
        TblUser u = userRepository.findById(me).orElse(null);
        if (u == null || !"Y".equals(u.getActvtnYn())) return fail(r, "이미 탈퇴한 계정이에요.");

        boolean sns = u.getPassword() == null || !u.getPassword().startsWith("$2");   // 비밀번호가 없는 계정(소셜로만 가입)
        if (sns) {
            if (!u.getLoginId().equals(String.valueOf(params.get("confirm")))) return fail(r, "확인을 위해 아이디를 정확히 입력해 주세요.");
        } else {
            String pw = params.get("password") == null ? "" : params.get("password").toString();
            if (!passwordEncoder.matches(pw, u.getPassword())) return fail(r, ResponseCode.CURRENT_PW_NOT_EQ.getMessage());
        }

        List<Map<String, Object>> owned = jdbc.queryForList("""
                SELECT c.comm_sn, COALESCE(c.comm_dspl_nm, c.comm_nm) AS name,
                       (SELECT COUNT(*) FROM knowra_community.tbl_comm_mbr m WHERE m.comm_sn = c.comm_sn AND m.stat = 'ACTIVE' AND m.user_sn <> ?) AS others
                FROM knowra_community.tbl_comm c JOIN knowra_community.tbl_comm_mbr o ON o.comm_sn = c.comm_sn
                WHERE o.user_sn = ? AND o.role = 'OWNER' AND o.stat = 'ACTIVE' AND c.stat = 'Y'""", me, me);
        for (Map<String, Object> c : owned) {
            if (((Number) c.get("others")).longValue() > 0)
                return fail(r, com.knowra.cmm.i18n.Msg.t("「{0}」의 주인이에요. 커뮤니티 멤버 목록에서 다른 멤버에게 주인을 넘긴 뒤 탈퇴해 주세요.", c.get("name")));
        }


        jdbc.update("UPDATE knowra_user.tbl_user SET delete_req_at = ? WHERE user_sn = ?", java.time.LocalDateTime.now(), me);
        sessionService.revokeOthers(me, null);   // 이 기기까지 전부 — 다시 들어오면 그게 「취소」다
        java.time.LocalDate on = java.time.LocalDate.now().plusDays(GRACE_DAYS);
        if (u.getEmail() != null && mail.isEnabled()) {
            try {
                var st = stngs.findByUserSn(me);   // 메일은 그 사람의 설정 언어로
                String ml = st != null && st.getLang() != null ? st.getLang() : com.knowra.cmm.i18n.Lang.current();
                mail.send(u.getEmail(), com.knowra.cmm.i18n.Msg.tl(ml, "[knowra] 탈퇴 신청을 받았어요"),
                        com.knowra.cmm.i18n.Msg.tl(ml, "{0}님, knowra 입니다.\n\n탈퇴 신청을 받았어요. {1}에 글·댓글·커뮤니티 활동과 개인정보가 모두 지워져요.\n마음이 바뀌면 그 전에 다시 로그인만 하세요. 탈퇴가 취소돼요.\n\n직접 신청하지 않았다면 바로 로그인해서 비밀번호를 바꿔 주세요.\n",
                                u.getName() == null ? u.getNickName() : u.getName(), longDate(on, ml)));
            } catch (Exception ignore) { /* 메일은 덤 — 신청은 이미 됐다 */ }
        }
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        String lang = com.knowra.cmm.i18n.Lang.current();
        r.setResultMessage(com.knowra.cmm.i18n.Msg.tl(lang, "탈퇴 신청을 받았어요. {0}에 모두 지워지고, 그 전에 다시 로그인하면 취소돼요.", longDate(on, lang)));
        return r;
    }

    /** 그 언어의 긴 날짜 꼴 — 「2026년 10월 29일」 / 「October 29, 2026」 */
    private static String longDate(java.time.LocalDate d, String lang) {
        return d.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(com.knowra.cmm.i18n.Fmt.locale(lang)));
    }

    /** 로그인 마무리에서 — 탈퇴 신청 중이었으면 취소하고 true */
    public boolean cancel(long userSn) {
        return jdbc.update("UPDATE knowra_user.tbl_user SET delete_req_at = NULL WHERE user_sn = ? AND delete_req_at IS NOT NULL AND actvtn_yn = 'Y'", userSn) > 0;
    }

    /** 매일 새벽 — 유예가 끝난 계정을 지운다. 한 사람 실패가 나머지를 막지 않게 한 명씩 */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${knowra.deletion.cron:0 40 4 * * *}", zone = "Asia/Seoul")
    public void daily() { purgeDue(java.time.LocalDateTime.now()); }

    public int purgeDue(java.time.LocalDateTime now) {
        int n = 0;
        for (Long sn : jdbc.queryForList("SELECT user_sn FROM knowra_user.tbl_user WHERE delete_req_at <= ? AND actvtn_yn = 'Y'",
                Long.class, now.minusDays(GRACE_DAYS))) {
            try {
                if (Boolean.TRUE.equals(tx.execute(st -> purge(sn)))) n++;
            } catch (Exception e) {
                com.knowra.cmm.errlog.ErrorLog.report(e);
            }
        }
        return n;
    }

    /**
     * 실제로 지운다. 신청한 뒤 혼자이던 커뮤니티에 멤버가 생겼을 수 있다 — 그때는 막지 않고(본인은 이미 떠났다)
     * 운영진, 없으면 가장 오래된 멤버에게 주인을 넘긴다.
     */
    boolean purge(long me) {
        TblUser u = userRepository.findById(me).orElse(null);
        if (u == null || !"Y".equals(u.getActvtnYn())) return false;
        List<Map<String, Object>> owned = new java.util.ArrayList<>();
        for (Map<String, Object> c : jdbc.queryForList("""
                SELECT c.comm_sn FROM knowra_community.tbl_comm c JOIN knowra_community.tbl_comm_mbr o ON o.comm_sn = c.comm_sn
                WHERE o.user_sn = ? AND o.role = 'OWNER' AND o.stat = 'ACTIVE' AND c.stat = 'Y'""", me)) {
            Long heir = jdbc.query("""
                    SELECT user_sn FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND stat = 'ACTIVE' AND user_sn <> ?
                    ORDER BY CASE role WHEN 'ADMIN' THEN 0 ELSE 1 END, frst_crt_dt LIMIT 1""",
                    rs -> rs.next() ? rs.getLong(1) : null, c.get("comm_sn"), me);
            if (heir == null) { owned.add(c); continue; }   // 혼자 — 아래에서 닫는다
            jdbc.update("UPDATE knowra_community.tbl_comm_mbr SET role = 'OWNER' WHERE comm_sn = ? AND user_sn = ?", c.get("comm_sn"), heir);
            jdbc.update("UPDATE knowra_community.tbl_comm_mbr SET role = 'MEMBER' WHERE comm_sn = ? AND user_sn = ?", c.get("comm_sn"), me);
        }

        // 1) 글·댓글 — 기존 삭제 경로로
        for (Long postSn : jdbc.queryForList("SELECT post_sn FROM knowra_post.tbl_post WHERE user_sn = ? AND actvtn_yn = 'Y'", Long.class, me))
            postService.setPostDel(Map.of("postSn", postSn), me);
        for (Long cmtSn : jdbc.queryForList("SELECT post_cmt_sn FROM knowra_post.tbl_post_cmt WHERE user_sn = ? AND actvtn_yn = 'Y' AND stat = 'ACTIVE' ORDER BY post_cmt_sn DESC", Long.class, me))
            postService.setPostCmtDel(Map.of("postCmtSn", cmtSn), me);

        // 2) 반응 — 좋아요 수는 되돌린다
        jdbc.update("UPDATE knowra_post.tbl_post p SET like_cnt = GREATEST(0, p.like_cnt - 1) FROM knowra_post.tbl_post_like l WHERE l.post_sn = p.post_sn AND l.user_sn = ?", me);
        jdbc.update("DELETE FROM knowra_post.tbl_post_like WHERE user_sn = ?", me);
        for (String t : List.of("knowra_post.tbl_post_cmt_react", "knowra_community.tbl_comm_event_rsvp", "knowra_post.tbl_post_save",
                "knowra_post.tbl_post_save_folder", "knowra_post.tbl_post_hide", "knowra_post.tbl_post_mute", "knowra_user.tbl_keyword_watch", "knowra_post.tbl_post_draft", "knowra_post.tbl_series", "knowra_user.tbl_known_device", "knowra_post.tbl_series_sub", "knowra_community.tbl_comm_join_ans", "knowra_community.tbl_comm_modmail", "knowra_user.tbl_tag_follow", "knowra_user.tbl_push_sub",
                "knowra_user.tbl_user_notif", "knowra_user.tbl_user_interest_score", "knowra_user.tbl_user_tag",
                "knowra_user.tbl_user_badge", "knowra_user.tbl_user_action_log", "knowra_user.tbl_user_lgn_hstry"))
            jdbc.update("DELETE FROM " + t + " WHERE user_sn = ?", me);
        jdbc.update("DELETE FROM knowra_user.tbl_user_flwr WHERE flwr_user_sn = ? OR flwng_user_sn = ?", me, me);
        jdbc.update("DELETE FROM knowra_user.tbl_user_block WHERE user_sn = ? OR blocked_sn = ?", me, me);
        jdbc.update("DELETE FROM knowra_user.tbl_user_mute WHERE user_sn = ? OR muted_sn = ?", me, me);

        // 3) 커뮤니티 — 혼자인 커뮤니티는 닫고, 나머지는 나간다(멤버 수 반영)
        for (Map<String, Object> c : owned)
            jdbc.update("UPDATE knowra_community.tbl_comm SET stat = 'N', member_cnt = 0 WHERE comm_sn = ?", c.get("comm_sn"));
        jdbc.update("""
                UPDATE knowra_community.tbl_comm c SET member_cnt = GREATEST(0, c.member_cnt - 1)
                FROM knowra_community.tbl_comm_mbr m WHERE m.comm_sn = c.comm_sn AND m.user_sn = ? AND m.stat = 'ACTIVE' AND c.stat = 'Y'""", me);
        jdbc.update("UPDATE knowra_community.tbl_comm_mbr SET stat = 'WITHDRAWN', role = 'MEMBER', notif_yn = 'N', mdfcn_dt = ? WHERE user_sn = ? AND stat <> 'WITHDRAWN'", java.time.LocalDateTime.now(), me);

        // 4) 개인정보 지우기 — 줄은 남긴다(대화·글 자리의 주인 표시용)
        if (u.getPfp() != null) {
            fileUtil.deleteStored(u.getPfp());
            var pfp = u.getPfp();
            u.setPfp(null);
            comFileRepository.delete(pfp);
        }
        byte[] b = new byte[24];
        random.nextBytes(b);
        u.setPassword(passwordEncoder.encode(HexFormat.of().formatHex(b)));
        u.setLoginId("deleted_" + me);
        u.setNickName("탈퇴회원" + me);
        u.setName("탈퇴한 사용자");
        u.setEmail(null);
        u.setPhone(null);
        u.setBio(null);
        u.setInterest(null);
        u.setSnsId(null);   // ⚠️ updatable=false 라 저장되지 않는다 — 실제로 푸는 건 아래 연결 표 삭제
        snsLinks.deleteAll(me);   // 같은 소셜 계정으로 다시 가입할 수 있게
        u.setSnsName(null);
        u.setActvtnYn("N");
        userRepository.save(u);
        jdbc.update("UPDATE knowra_user.tbl_user SET delete_req_at = NULL WHERE user_sn = ?", me);

        sessionService.revokeOthers(me, null);
        return true;
    }

    private static ResultVO fail(ResultVO r, String msg) {
        r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
        r.setResultMessage(msg);
        return r;
    }
}
