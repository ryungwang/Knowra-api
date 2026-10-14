package com.knowra.admin.service;

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

    public ResultVO getStats() {
        ResultVO resultVO = new ResultVO();
        try {
            LocalDateTime today = LocalDate.now().atStartOfDay();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("users",          userRepository.count());
            m.put("usersToday",     userRepository.countByFrstCrtDtAfter(today));
            m.put("usersSuspended", userRepository.countByStatus("S"));
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
            Page<TblReport> p = reportRepository.findAllByStatOrderByFrstCrtDtDesc(stat, PageRequest.of(page, PAGE_SIZE));

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
        userRepository.findById(r.getReporterSn()).ifPresent(u -> row.put("reporter", userBrief(u)));
        row.put("sameTargetCnt", reportRepository.countByTargetKindAndTargetSn(r.getTargetKind(), r.getTargetSn()));

        Map<String, Object> t = new LinkedHashMap<>();
        boolean gone = true;
        switch (r.getTargetKind()) {
            case "POST" -> {
                TblPost p = postRepository.findByPostSn(r.getTargetSn());
                if (p != null) {
                    gone = !"Y".equals(p.getActvtnYn());
                    t.put("postSn", p.getPostSn()); t.put("title", p.getPostTtl());
                    t.put("preview", snippet(p.getPostCntnt()));
                    userRepository.findById(p.getUserSn()).ifPresent(u -> t.put("author", userBrief(u)));
                }
            }
            case "CMT" -> {
                TblPostCmt c = cmtRepository.findById(r.getTargetSn()).orElse(null);
                if (c != null) {
                    gone = !"ACTIVE".equals(c.getStat());
                    t.put("postCmtSn", c.getPostCmtSn()); t.put("postSn", c.getPostSn());
                    t.put("preview", snippet(c.getCmtCntnt()));
                    userRepository.findById(c.getUserSn()).ifPresent(u -> t.put("author", userBrief(u)));
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
            boolean done = postService.adminDeletePost(postSn, admin);
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

    /** params: postCmtSn */
    public ResultVO deleteCmt(Map<String, Object> params, Long admin) {
        ResultVO resultVO = new ResultVO();
        try {
            long cmtSn = Long.parseLong(String.valueOf(params.get("postCmtSn")));
            boolean done = postService.adminDeleteCmt(cmtSn, admin);
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

    /** params: userSn, suspend(true|false). 관리자 자신은 정지할 수 없다. */
    @Transactional
    public ResultVO setUserSuspend(Map<String, Object> params, Long admin) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = Long.parseLong(String.valueOf(params.get("userSn")));
            boolean suspend = Boolean.parseBoolean(String.valueOf(params.get("suspend")));
            if (userSn == admin) throw new IllegalArgumentException("자기 자신은 정지할 수 없습니다.");
            TblUser u = userRepository.findById(userSn).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            u.setStatus(suspend ? "S" : "Y");
            userRepository.save(u);
            // 정지된 사람의 열린 알림 소켓도 닫는다(새 요청만 막으면 이미 열린 소켓으로 알림이 계속 간다)
            // 정지하면 모든 세션을 끊는다 — 소켓만 닫던 시절엔 이미 받은 토큰으로 한 시간 동안 글·DM 을 계속 썼다
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
}
