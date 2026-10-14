package com.knowra.safety.service;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.safety.entity.TblPostHide;
import com.knowra.safety.entity.TblReport;
import com.knowra.safety.entity.TblUserBlock;
import com.knowra.safety.repository.TblPostHideRepository;
import com.knowra.safety.repository.TblReportRepository;
import com.knowra.safety.repository.TblUserBlockRepository;
import com.knowra.post.entity.TblPost;
import com.knowra.post.repository.TblPostRepository;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 신고 · 차단 · 숨기기.
 * 차단은 양방향이다 — 내가 차단한 사람의 글·댓글이 내게 안 보이고, 내 글·댓글도 그 사람에게 안 보인다.
 * 채팅·알림도 서로 끊긴다. 숨기기는 글 하나만 내 피드에서 치운다.
 */
@Service
@RequiredArgsConstructor
public class SafetyService {

    public static final Set<String> REASONS = Set.of("SPAM", "ABUSE", "SEXUAL", "ILLEGAL", "PRIVACY", "OTHER");
    public static final Set<String> KINDS   = Set.of("POST", "CMT", "USER", "MSG");

    private final TblReportRepository reportRepository;
    private final TblUserBlockRepository blockRepository;
    private final TblPostHideRepository hideRepository;
    private final TblUserRepository userRepository;
    private final TblPostRepository postRepository;
    private final com.knowra.post.service.PostQuery postQuery;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ReportAutoHide autoHide;

    /** params: targetKind(POST|CMT|USER|MSG), targetSn, reason, detail? — 같은 대상은 한 번만. */
    @Transactional
    public ResultVO setReport(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            String kind   = String.valueOf(params.get("targetKind"));
            long   target = Long.parseLong(String.valueOf(params.get("targetSn")));
            String reason = String.valueOf(params.get("reason"));
            String detail = params.get("detail") == null ? null : String.valueOf(params.get("detail")).trim();
            if (!KINDS.contains(kind) || !REASONS.contains(reason)) throw new IllegalArgumentException("신고 종류나 사유가 올바르지 않습니다.");
            if ("USER".equals(kind) && target == me) throw new IllegalArgumentException("자기 자신은 신고할 수 없습니다.");
            // DM 은 그 대화방에 있는 사람만 신고할 수 있다 — 남의 대화를 번호로 찔러 운영자 화면에 올리지 못하게
            if ("MSG".equals(kind)) {
                Long sender = jdbc.query("""
                        SELECT m.user_sn FROM knowra_user.tbl_chat_msg m
                        JOIN knowra_user.tbl_chat_room_mbr r ON r.room_sn = m.room_sn AND r.user_sn = ?
                        WHERE m.msg_sn = ?""", rs -> rs.next() ? rs.getLong(1) : null, me, target);
                if (sender == null) throw new IllegalArgumentException("메시지를 찾을 수 없습니다.");
                if (sender == me) throw new IllegalArgumentException("내 메시지는 신고할 수 없습니다.");
            }
            if (reportRepository.findByReporterSnAndTargetKindAndTargetSn(me, kind, target).isPresent()) {
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage("이미 신고한 대상이에요. 검토 중입니다.");
                return resultVO;
            }
            if (detail != null && detail.length() > 500) detail = detail.substring(0, 500);
            // 글·댓글이면 그 커뮤니티 운영진 신고함에도 올린다
            Long commSn = switch (kind) {
                case "POST" -> jdbc.query("SELECT comm_sn FROM knowra_post.tbl_post WHERE post_sn = ?", rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null, target);
                case "CMT"  -> jdbc.query("SELECT p.comm_sn FROM knowra_post.tbl_post_cmt c JOIN knowra_post.tbl_post p ON p.post_sn = c.post_sn WHERE c.post_cmt_sn = ?",
                        rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null, target);
                default -> null;
            };
            reportRepository.save(TblReport.builder().reporterSn(me).targetKind(kind).targetSn(target).reason(reason).detail(detail)
                    .commSn(commSn).commStat(commSn == null ? null : "OPEN").build());
            autoHide.afterReport(kind, target);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage("신고가 접수됐어요. 확인 후 조치할게요.");
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    /** params: nickName, block(true|false) */
    @Transactional
    public ResultVO setBlock(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            TblUser target = userRepository.findByNickName(String.valueOf(params.get("nickName")))
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            if (target.getUserSn() == me) throw new IllegalArgumentException("자기 자신은 차단할 수 없습니다.");
            boolean block = Boolean.parseBoolean(String.valueOf(params.get("block")));
            Optional<TblUserBlock> existing = blockRepository.findByUserSnAndBlockedSn(me, target.getUserSn());
            if (block && existing.isEmpty()) {
                blockRepository.save(TblUserBlock.builder().userSn(me).blockedSn(target.getUserSn()).build());
            } else if (!block && existing.isPresent()) {
                blockRepository.delete(existing.get());
            }
            resultVO.putResult("blocked", block);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 내가 차단한 사람 목록 — 설정 화면용. */
    public ResultVO getBlockList(Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (TblUserBlock b : blockRepository.findAllByUserSn(me)) {
                userRepository.findById(b.getBlockedSn()).ifPresent(u -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("userSn", u.getUserSn()); row.put("nickName", u.getNickName()); row.put("name", u.getName());
                    row.put("blockedAt", b.getFrstCrtDt());
                    list.add(row);
                });
            }
            resultVO.putResult("list", list);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** params: postSn, hide(true|false) */
    @Transactional
    public ResultVO setPostHide(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long postSn = Long.parseLong(String.valueOf(params.get("postSn")));
            boolean hide = Boolean.parseBoolean(String.valueOf(params.get("hide")));
            Optional<TblPostHide> existing = hideRepository.findByUserSnAndPostSn(me, postSn);
            // 볼 수 없는 글은 숨길 수도 없다 — 예전엔 아무 번호나 숨긴 뒤 숨김 목록으로 비공개 글 제목을 읽었다
            if (hide && existing.isEmpty() && !postQuery.accessible(postSn, me)) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("숨길 수 없는 글이에요.");
                return resultVO;
            }
            if (hide && existing.isEmpty()) hideRepository.save(TblPostHide.builder().userSn(me).postSn(postSn).build());
            else if (!hide && existing.isPresent()) hideRepository.delete(existing.get());
            resultVO.putResult("hidden", hide);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 내가 숨긴 글 목록 — 설정 화면용. 숨긴 글은 피드에서도, 직접 링크로도 안 보이므로
     * <b>여기가 되돌릴 수 있는 유일한 자리</b>다. 삭제된 글은 목록에서도 뺀다.
     */
    public ResultVO getHideList(Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (TblPostHide h : hideRepository.findAllByUserSnOrderByFrstCrtDtDesc(me)) {
                postRepository.findById(h.getPostSn()).filter(p -> postQuery.accessible(p.getPostSn(), me)).ifPresent(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("postSn", p.getPostSn()); row.put("title", p.getPostTtl());
                    userRepository.findById(p.getUserSn()).ifPresent(u -> { row.put("nickName", u.getNickName()); row.put("name", u.getName()); });
                    row.put("hiddenAt", h.getFrstCrtDt());
                    list.add(row);
                });
            }
            resultVO.putResult("list", list);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 둘 중 하나라도 상대를 차단했는가 — 채팅·알림이 쓴다. */
    public boolean blockedEitherWay(long a, long b) {
        return blockRepository.existsByUserSnAndBlockedSn(a, b) || blockRepository.existsByUserSnAndBlockedSn(b, a);
    }
}
