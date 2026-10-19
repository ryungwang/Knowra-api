package com.knowra.admin.service;

import com.knowra.cmm.i18n.Msg;
import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.community.repository.TblCommRepository;
import com.knowra.post.entity.TblPost;
import com.knowra.post.entity.TblPostCmt;
import com.knowra.post.repository.TblPostCmtRepository;
import com.knowra.post.repository.TblPostRepository;
import com.knowra.post.service.PostService;
import com.knowra.safety.entity.TblReport;
import com.knowra.safety.repository.TblReportRepository;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 운영 관리자 콘솔 — 신고 큐 · 사용자 정지 · 글/댓글 삭제 · 통계.
 *
 * <p>권한은 전부 {@code /api/admin/**} 의 ROLE_ADMIN 매처가 본다(토큰 role 클레임).
 * 여기서는 "관리자가 맞다"를 전제로 동작만 한다.
 *
 * <p>정지(status='S')는 로그인·리프레시를 막는다. 이미 발급된 액세스 토큰은 만료까지 산다 —
 * 토큰을 일일이 추적하지 않는 대신 수명이 짧다. 즉시 끊어야 하면 비밀번호 재설정 경로를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class AdminConsoleService {

    public static final int PAGE_SIZE = 30;
    private static final Set<String> REPORT_STATS = Set.of("OPEN", "RESOLVED", "DISMISSED");

    private final TblReportRepository reportRepository;
    private final TblUserRepository userRepository;
    private final TblPostRepository postRepository;
    private final TblPostCmtRepository cmtRepository;
    private final TblCommRepository commRepository;
    private final PostService postService;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.knowra.session.SessionService sessionService;
    private final com.knowra.safety.service.ReportAutoHide autoHide;
    private final AdminLogService adminLog;
    private final com.knowra.safety.appeal.ModDecisionService decisions;

    public ResultVO getStats() {
        ResultVO resultVO = new ResultVO();
        try {
            LocalDateTime today = LocalDate.now().atStartOfDay();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("users",          userRepository.count());
            m.put("usersToday",     userRepository.countByFrstCrtDtAfter(today));
            m.put("usersSuspended", userRepository.countByStatus("S"));
            m.put("usersDormant",   userRepository.countByStatus("D"));   // 1년 넘게 안 들어와 휴면(DormantService)
            m.put("posts",          postRepository.countByActvtnYn("Y"));
            m.put("postsToday",     postRepository.countByActvtnYnAndFrstCrtDtAfter("Y", today));
            m.put("communities",    commRepository.countByStat("Y"));
            m.put("reportsOpen",    reportRepository.countByStat("OPEN"));
            m.put("errorsOpen",     jdbc.queryForObject("SELECT COUNT(*) FROM knowra_com.tbl_err_log WHERE RESOLVED = FALSE", Integer.class));
            resultVO.putResult("stats", m);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** params: stat(OPEN|RESOLVED|DISMISSED, 기본 OPEN), page(0부터) */
    public ResultVO getReportList(Map<String, Object> params) {
        ResultVO resultVO = new ResultVO();
        try {
            String stat = params.get("stat") == null ? "OPEN" : String.valueOf(params.get("stat"));
            if (!REPORT_STATS.contains(stat)) stat = "OPEN";
            int page = params.get("page") == null ? 0 : Integer.parseInt(String.valueOf(params.get("page")));
            Page<TblReport> p = reportRepository.findQueue(stat, PageRequest.of(page, PAGE_SIZE));

            List<Map<String, Object>> list = new ArrayList<>();
            for (TblReport r : p.getContent()) list.add(toRow(r));
            resultVO.putResult("list", list);
            resultVO.putResult("hasMore", p.hasNext());
            resultVO.putResult("total", p.getTotalElements());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 신고 한 건 + 대상 미리보기(글 제목 / 댓글 본문 / 사용자). 대상이 이미 지워졌으면 targetGone. */
    private Map<String, Object> toRow(TblReport r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportSn", r.getReportSn()); row.put("targetKind", r.getTargetKind()); row.put("targetSn", r.getTargetSn());
        row.put("reason", r.getReason()); row.put("detail", r.getDetail()); row.put("stat", r.getStat());
        row.put("createdAt", r.getFrstCrtDt()); row.put("handledAt", r.getHandledAt());
        // 커뮤니티 운영진이 이미 손댔는지 — 「문제없음」으로 덮은 걸 사이트 관리자가 알아보게
        if (r.getCommSn() != null) { row.put("commStat", r.getCommStat()); row.put("commSn", r.getCommSn()); }
        if (r.getReporterSn() == null) row.put("auto", true);   // 자동 검수(AI)가 올린 신고
        else userRepository.findById(r.getReporterSn()).ifPresent(u -> row.put("reporter", userBrief(u)));
        row.put("sameTargetCnt", reportRepository.countByTargetKindAndTargetSn(r.getTargetKind(), r.getTargetSn()));

        Map<String, Object> t = new LinkedHashMap<>();
        boolean gone = true;
        switch (r.getTargetKind()) {
            case "POST" -> {
                TblPost p = postRepository.findByPostSn(r.getTargetSn());
                if (p != null) {
                    gone = !"Y".equals(p.getActvtnYn());
                    t.put("autoHidden", "REVIEW".equals(p.getStat()));
                    t.put("postSn", p.getPostSn()); t.put("title", p.getPostTtl());
                    t.put("preview", snippet(p.getPostCntnt()));
                    userRepository.findById(p.getUserSn()).ifPresent(u -> t.put("author", userBrief(u)));
                }
            }
            case "CMT" -> {
                TblPostCmt c = cmtRepository.findById(r.getTargetSn()).orElse(null);
                if (c != null) {
                    gone = !"ACTIVE".equals(c.getStat()) && !"REVIEW".equals(c.getStat());
                    t.put("autoHidden", "REVIEW".equals(c.getStat()));
                    t.put("postCmtSn", c.getPostCmtSn()); t.put("postSn", c.getPostSn());
                    t.put("preview", snippet(c.getCmtCntnt()));
                    userRepository.findById(c.getUserSn()).ifPresent(u -> t.put("author", userBrief(u)));
                }
            }
            case "MSG" -> {
                // 신고된 메시지와 바로 앞 대화 몇 줄 — 앞뒤 없이는 괴롭힘인지 농담인지 판단할 수 없다. 그 이상은 보이지 않는다
                List<Map<String, Object>> ctx = jdbc.queryForList("""
                        SELECT m.msg_sn, m.user_sn, u.nick_name, u.name, m.msg_cntnt, m.img_url, m.hidden_at, m.deleted_at, m.frst_crt_dt
                        FROM knowra_user.tbl_chat_msg m JOIN knowra_user.tbl_user u ON u.user_sn = m.user_sn
                        WHERE m.room_sn = (SELECT room_sn FROM knowra_user.tbl_chat_msg WHERE msg_sn = ?) AND m.msg_sn <= ?
                        ORDER BY m.msg_sn DESC LIMIT 4""", r.getTargetSn(), r.getTargetSn());
                if (!ctx.isEmpty() && ((Number) ctx.get(0).get("msg_sn")).longValue() == r.getTargetSn()) {
                    gone = false;
                    Map<String, Object> msg = ctx.get(0);
                    t.put("msgSn", r.getTargetSn());
                    t.put("preview", snippet(String.valueOf(msg.get("msg_cntnt"))) + (msg.get("img_url") != null ? " [" + Msg.t("사진") + "]" : ""));
                    t.put("hidden", msg.get("hidden_at") != null);
                    t.put("senderDeleted", msg.get("deleted_at") != null);   // 보낸 사람이 지운 뒤에도 원문은 보인다
                    userRepository.findById(((Number) msg.get("user_sn")).longValue()).ifPresent(u -> t.put("author", userBrief(u)));
                    List<Map<String, Object>> before = new ArrayList<>();
                    for (int i = ctx.size() - 1; i >= 1; i--) {
                        Map<String, Object> c = ctx.get(i);
                        before.add(Map.of("nickName", c.get("nick_name"), "text", snippet(String.valueOf(c.get("msg_cntnt"))) + (c.get("img_url") != null ? " [" + Msg.t("사진") + "]" : "")));
                    }
                    t.put("context", before);
                }
            }
            case "USER" -> {
                TblUser u = userRepository.findById(r.getTargetSn()).orElse(null);
                if (u != null) { gone = false; t.put("author", userBrief(u)); }
            }
            default -> {}
        }
        row.put("target", t); row.put("targetGone", gone);
        return row;
    }

    private static Map<String, Object> userBrief(TblUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userSn", u.getUserSn()); m.put("nickName", u.getNickName()); m.put("name", u.getName()); m.put("status", u.getStatus());
        m.put("siteRole", u.getSiteRole());
        if ("S".equals(u.getStatus())) { m.put("suspendedUntil", u.getSuspendedUntil()); m.put("suspendReason", u.getSuspendReason()); }
        return m;
    }

    private static String snippet(String s) {
        if (s == null) return "";
        String plain = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return plain.length() > 140 ? plain.substring(0, 140) + "…" : plain;
    }

    /** params: reportSn, action(RESOLVE|DISMISS) */
    @Transactional
    public ResultVO handleReport(Map<String, Object> params, Long admin) {
        ResultVO resultVO = new ResultVO();
        try {
            long reportSn = Long.parseLong(String.valueOf(params.get("reportSn")));
            String action = String.valueOf(params.get("action"));
            TblReport r = reportRepository.findById(reportSn).orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다."));
            String stat = switch (action) { case "RESOLVE" -> "RESOLVED"; case "DISMISS" -> "DISMISSED"; case "REOPEN" -> "OPEN"; default -> throw new IllegalArgumentException("알 수 없는 처리입니다."); };
            r.setStat(stat); r.setHandledBy("OPEN".equals(stat) ? null : admin); r.setHandledAt("OPEN".equals(stat) ? null : LocalDateTime.now());
            reportRepository.save(r);
            // 기각이면 신고 누적으로 가려 둔 것을 되돌린다 — 몰려와서 신고로 남의 글을 가리는 걸 여기서 푼다
            if ("DISMISSED".equals(stat)) autoHide.restore(r.getTargetKind(), r.getTargetSn());
            adminLog.record(admin, "REPORT_" + action, "REPORT", reportSn,
                    com.knowra.community.modlog.ModLogDetail.of(java.util.Map.of("kind", r.getTargetKind(), "sn", r.getTargetSn(), "reason", r.getReason())));
            resultVO.putResult("report", toRow(r));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.UPDATE_ERROR.getMessage());
        }
        return resultVO;
    }

    /** params: postSn — 작성자·커뮤니티 권한과 무관하게 지운다. */
    public ResultVO deletePost(Map<String, Object> params, Long admin) {
        ResultVO resultVO = new ResultVO();
        try {
            long postSn = Long.parseLong(String.valueOf(params.get("postSn")));
            TblPost before = postRepository.findById(postSn).orElse(null);
            String title = before == null ? null : before.getPostTtl();
            boolean done = postService.adminDeletePost(postSn, admin);
            if (done) adminLog.record(admin, "POST_DELETE", "POST", postSn, title);
            // 당사자에게 무엇을 왜 지웠는지 알리고 이의를 받는다(DSA 17조) — 사유는 코드로
            if (done && before != null) decisions.record(before.getUserSn(), "POST_REMOVE", postSn, String.valueOf(params.get("reason")), (String) params.get("detail"), title, admin);
            resultVO.putResult("deleted", done);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            if (!done) resultVO.setResultMessage("이미 삭제된 글이에요.");
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.DELETE_ERROR.getMessage());
        }
        return resultVO;
    }

    /** params: msgSn — DM 가리기(신고 처리). 지우지 않는다 — 재신고 때 다시 본다 */
    public ResultVO hideMsg(Map<String, Object> params, Long admin) {
        ResultVO resultVO = new ResultVO();
        try {
            long msgSn = Long.parseLong(String.valueOf(params.get("msgSn")));
            int n = jdbc.update("UPDATE knowra_user.tbl_chat_msg SET hidden_at = ? WHERE msg_sn = ? AND hidden_at IS NULL", java.time.LocalDateTime.now(), msgSn);
            resultVO.putResult("hidden", n == 1);
            if (n == 1) adminLog.record(admin, "MSG_HIDE", "MSG", msgSn, null);
            if (n == 1) jdbc.query("SELECT user_sn, msg_cntnt FROM knowra_user.tbl_chat_msg WHERE msg_sn = ?", rs -> {
                decisions.record(rs.getLong(1), "MSG_HIDE", msgSn, String.valueOf(params.get("reason")), (String) params.get("detail"), snippet(rs.getString(2)), admin);
            }, msgSn);
            if (n == 0) resultVO.setResultMessage("이미 가려졌거나 없는 메시지예요.");
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.UPDATE_ERROR.getMessage());
        }
        return resultVO;
    }

    /** params: postCmtSn */
    public ResultVO deleteCmt(Map<String, Object> params, Long admin) {
        ResultVO resultVO = new ResultVO();
        try {
            long cmtSn = Long.parseLong(String.valueOf(params.get("postCmtSn")));
            var cmt = cmtRepository.findById(cmtSn).orElse(null);
            String text = cmt == null ? null : snippet(cmt.getCmtCntnt());
            boolean done = postService.adminDeleteCmt(cmtSn, admin);
            if (done) adminLog.record(admin, "CMT_DELETE", "CMT", cmtSn, text);
            if (done && cmt != null) decisions.record(cmt.getUserSn(), "CMT_REMOVE", cmtSn, String.valueOf(params.get("reason")), (String) params.get("detail"), text, admin);
            resultVO.putResult("deleted", done);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            if (!done) resultVO.setResultMessage("이미 삭제된 댓글이에요.");
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.DELETE_ERROR.getMessage());
        }
        return resultVO;
    }

    /** params: q, page */
    public ResultVO getUserList(Map<String, Object> params) {
        ResultVO resultVO = new ResultVO();
        try {
            String q = params.get("q") == null ? "" : String.valueOf(params.get("q")).trim();
            int page = params.get("page") == null ? 0 : Integer.parseInt(String.valueOf(params.get("page")));
            Page<TblUser> p = userRepository.searchForAdmin(q, PageRequest.of(page, PAGE_SIZE));
            List<Map<String, Object>> list = new ArrayList<>();
            for (TblUser u : p.getContent()) {
                Map<String, Object> m = userBrief(u);
                m.put("loginId", u.getLoginId()); m.put("email", u.getEmail()); m.put("joinedAt", u.getFrstCrtDt());
                m.put("deleted", !"Y".equals(u.getActvtnYn()));   // 탈퇴 — 역할·정지를 걸 수 없다
                m.put("reportedCnt", reportRepository.countByTargetKindAndTargetSn("USER", u.getUserSn()));
                list.add(m);
            }
            resultVO.putResult("list", list);
            resultVO.putResult("hasMore", p.hasNext());
            resultVO.putResult("total", p.getTotalElements());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * params: userSn, suspend(true|false), days(1~365, 없거나 0 이면 무기한), reason(200자, 본인 로그인 화면에 보인다).
     * 관리자 자신은 정지할 수 없다. 이미 정지 중인 사람에게 다시 걸면 기간·사유를 새로 덮는다.
     */
    @Transactional
    public ResultVO setUserSuspend(Map<String, Object> params, Long admin) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = Long.parseLong(String.valueOf(params.get("userSn")));
            boolean suspend = Boolean.parseBoolean(String.valueOf(params.get("suspend")));
            if (userSn == admin) throw new IllegalArgumentException("자기 자신은 정지할 수 없습니다.");
            TblUser u = userRepository.findById(userSn).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            // 운영진끼리 정지로 다투지 않게 — 관리자가 역할을 먼저 내린 뒤에 정지한다
            if (suspend && !"USER".equals(u.getSiteRole())) throw new IllegalArgumentException("운영진은 역할을 먼저 내린 뒤 정지할 수 있어요.");
            if (suspend) {
                Object d = params.get("days");
                int days = d == null || String.valueOf(d).isBlank() ? 0 : Integer.parseInt(String.valueOf(d));
                if (days < 0 || days > 365) throw new IllegalArgumentException("정지 기간은 1~365일이에요.");
                String reason = params.get("reason") == null ? null : String.valueOf(params.get("reason")).strip();
                if (reason != null && reason.length() > 200) throw new IllegalArgumentException("사유는 200자까지예요.");
                u.setStatus("S");
                u.setSuspendedUntil(days == 0 ? null : java.time.LocalDateTime.now().plusDays(days));
                u.setSuspendReason(reason == null || reason.isEmpty() ? null : reason);
            } else {
                u.setStatus("Y"); u.setSuspendedUntil(null); u.setSuspendReason(null);
            }
            userRepository.save(u);
            // 상세는 값만(JSON) — 관리자 화면이 보는 사람 언어로 「무기한」·「…까지」를 만든다
            adminLog.record(admin, suspend ? "USER_SUSPEND" : "USER_UNSUSPEND", "USER", userSn, suspend
                    ? com.knowra.community.modlog.ModLogDetail.ban(u.getSuspendedUntil(), u.getSuspendReason())
                    : null);
            // 정지된 사람의 열린 알림 소켓도 닫는다(새 요청만 막으면 이미 열린 소켓으로 알림이 계속 간다)
            // 정지하면 모든 세션을 끊는다 — 소켓만 닫던 시절엔 이미 받은 토큰으로 한 시간 동안 글·DM 을 계속 썼다
            if (suspend) decisions.record(userSn, "SUSPEND", null, String.valueOf(params.get("reasonCode")), u.getSuspendReason(),
                    u.getSuspendedUntil() == null ? null : u.getSuspendedUntil().toString(), admin);
            if (suspend) sessionService.revokeOthers(userSn, null);
            if (suspend) publisher.publishEvent(new com.knowra.session.SessionRevokedEvent(userSn, null));
            resultVO.putResult("user", userBrief(u));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.UPDATE_ERROR.getMessage());
        }
        return resultVO;
    }

    private static final List<String> SITE_ROLES = List.of("USER", "MOD", "ADMIN");

    /**
     * 사이트 운영 역할 바꾸기(관리자만 — SecurityConfig). params: userSn, role(USER|MOD|ADMIN).
     * 자기 역할은 못 바꾼다 — 마지막 관리자가 스스로 내려 아무도 관리할 수 없게 되는 일을 막는다.
     * 올리면 다음 토큰 갱신(길어야 액세스 토큰 수명)부터, 내리면 세션을 끊어 바로 반영한다.
     */
    @Transactional
    public ResultVO setUserRole(Map<String, Object> params, Long admin) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = Long.parseLong(String.valueOf(params.get("userSn")));
            String role = String.valueOf(params.get("role"));
            if (!SITE_ROLES.contains(role)) throw new IllegalArgumentException("지원하지 않는 역할입니다.");
            if (userSn == admin) throw new IllegalArgumentException("자기 역할은 바꿀 수 없어요.");
            TblUser u = userRepository.findById(userSn).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            if (!"Y".equals(u.getActvtnYn())) throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
            if (!"USER".equals(role) && "S".equals(u.getStatus())) throw new IllegalArgumentException("정지된 사람에게는 역할을 줄 수 없어요.");
            String from = u.getSiteRole();
            if (!role.equals(from)) {
                u.setSiteRole(role);
                userRepository.save(u);
                adminLog.record(admin, "USER_ROLE", "USER", userSn, com.knowra.community.modlog.ModLogDetail.of(Map.of("roleFrom", from, "roleTo", role)));
                // 내렸으면 이미 받은 토큰의 관리 권한이 남지 않게 모든 세션을 끊는다
                if (SITE_ROLES.indexOf(role) < SITE_ROLES.indexOf(from)) {
                    sessionService.revokeOthers(userSn, null);
                    publisher.publishEvent(new com.knowra.session.SessionRevokedEvent(userSn, null));
                }
            }
            resultVO.putResult("user", userBrief(u));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.UPDATE_ERROR.getMessage());
        }
        return resultVO;
    }
}
