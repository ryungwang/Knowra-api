package com.knowra.community.invite;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.common.entity.TblComFile;
import com.knowra.community.entity.TblComm;
import com.knowra.community.entity.TblCommMbr;
import com.knowra.community.repository.TblCommMbrRepository;
import com.knowra.community.repository.TblCommRepository;
import com.knowra.community.service.CommunityService;
import com.knowra.user.repository.TblUserRepository;
import com.knowra.wiki.WikiService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 커뮤니티 초대 링크. 규칙은 V18 머리말 참고.
 *
 * <p>코드는 10자 무작위(62진수, 약 59비트) — 추측으로 남의 비공개 커뮤니티 링크를 찾을 수 없게.
 * 같은 사람이 같은 커뮤니티에서 다시 누르면 하루 이상 남은 기존 링크를 돌려준다(링크가 쌓이지 않게).
 */
@Service
@RequiredArgsConstructor
public class InviteService {

    static final int VALID_DAYS = 7;
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final TblCommRepository commRepository;
    private final TblCommMbrRepository mbrRepository;
    private final TblUserRepository userRepository;
    private final CommunityService communityService;
    private final WikiService wikiService;

    private static boolean isOpen(TblComm c) {
        return "public".equals(c.getPrvcyStng()) || "anonymous".equals(c.getPrvcyStng());
    }

    private Optional<TblCommMbr> activeMember(long commSn, long userSn) {
        return mbrRepository.findByCommSnAndUserSn(commSn, userSn)
                .filter(m -> "ACTIVE".equals(m.getStat()) && !"N".equals(m.getActvtnYn()));
    }

    private static boolean isManager(TblCommMbr m) {
        return "OWNER".equals(m.getRole()) || "ADMIN".equals(m.getRole());
    }

    /** params: commSn. 멤버만. 승인제·비공개는 운영진만. */
    @Transactional
    public ResultVO create(Map<String, Object> params, long me) {
        ResultVO r = new ResultVO();
        try {
            long commSn = Long.parseLong(String.valueOf(params.get("commSn")));
            TblComm comm = commRepository.findByCommSn(commSn);
            if (comm == null || !"Y".equals(comm.getStat())) return fail(r, "없는 커뮤니티예요.");
            Optional<TblCommMbr> m = activeMember(commSn, me);
            if (m.isEmpty()) return fail(r, "멤버만 초대할 수 있어요.");
            if (!isOpen(comm) && !isManager(m.get())) return fail(r, "승인제 커뮤니티는 운영진만 초대 링크를 만들 수 있어요.");

            List<Map<String, Object>> reuse = jdbc.queryForList("""
                    SELECT INVITE_CODE, EXPIRES_AT FROM knowra_community.tbl_comm_invite
                    WHERE COMM_SN = ? AND CREATED_BY = ? AND NOT REVOKED AND EXPIRES_AT > ?
                    ORDER BY EXPIRES_AT DESC LIMIT 1
                    """, commSn, me, Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
            String code;
            Object expiresAt;
            if (!reuse.isEmpty()) {
                code = (String) reuse.get(0).get("invite_code");
                expiresAt = reuse.get(0).get("expires_at");
            } else {
                code = newCode();
                LocalDateTime exp = LocalDateTime.now().plusDays(VALID_DAYS);
                jdbc.update("INSERT INTO knowra_community.tbl_comm_invite (INVITE_CODE, COMM_SN, CREATED_BY, EXPIRES_AT) VALUES (?, ?, ?, ?)",
                        code, commSn, me, Timestamp.valueOf(exp));
                expiresAt = Timestamp.valueOf(exp);
            }
            r.putResult("code", code);
            r.putResult("expiresAt", expiresAt);
            r.putResult("skipsApproval", !isOpen(comm)); // 화면 안내용: 이 링크로 오면 승인 없이 들어온다
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            fail(r, "초대 링크를 만들지 못했어요.");
        }
        return r;
    }

    /** 초대 받는 화면. 누구나(로그인 전에도 어떤 커뮤니티인지는 보여야 한다). */
    @Transactional(readOnly = true)
    public ResultVO view(String code, Long me) {
        ResultVO r = new ResultVO();
        try {
            Map<String, Object> inv = find(code);
            String why = invalidReason(inv);
            TblComm comm = inv == null ? null : commRepository.findByCommSn(((Number) inv.get("comm_sn")).longValue());
            if (why == null && (comm == null || !"Y".equals(comm.getStat()))) why = "없어진 커뮤니티예요.";
            r.putResult("valid", why == null);
            if (why != null) {
                r.putResult("reason", why);
                r.setResultCode(ResponseCode.SUCCESS.getCode());
                return r;
            }
            long inviter = ((Number) inv.get("created_by")).longValue();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("commSn", comm.getCommSn());
            c.put("commNm", comm.getCommNm());
            c.put("commDsplNm", comm.getCommDsplNm());
            c.put("commDesc", comm.getCommDesc());
            c.put("memberCnt", comm.getMemberCnt());
            c.put("prvcyStng", comm.getPrvcyStng());
            c.put("logoFile", fileOf(comm.getLogoFile()));
            c.put("bnrFile", fileOf(comm.getBnrFile()));
            r.putResult("community", c);
            userRepository.findById(inviter).ifPresent(u -> r.putResult("inviter", Map.of("nickName", u.getNickName(), "name", u.getName() == null ? "" : u.getName())));
            r.putResult("expiresAt", inv.get("expires_at"));
            r.putResult("hasRules", wikiService.hasRules(comm.getCommSn()));
            // 승인제인데 운영진 링크면 바로 멤버, 아니면 가입 신청으로 들어간다 — 누르기 전에 알려 준다
            r.putResult("skipsApproval", !isOpen(comm) && activeMember(comm.getCommSn(), inviter).map(InviteService::isManager).orElse(false));
            r.putResult("myStatus", me == null ? null
                    : mbrRepository.findByCommSnAndUserSn(comm.getCommSn(), me).map(TblCommMbr::getStat).orElse(null));
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            fail(r, "초대 정보를 불러오지 못했어요.");
        }
        return r;
    }

    /** params: code, agreeRules. 로그인 필요. */
    @Transactional
    public ResultVO accept(Map<String, Object> params, long me) {
        ResultVO r = new ResultVO();
        try {
            Map<String, Object> inv = find(String.valueOf(params.get("code")));
            String why = invalidReason(inv);
            if (why != null) return fail(r, why);
            long commSn = ((Number) inv.get("comm_sn")).longValue();
            TblComm comm = commRepository.findByCommSn(commSn);
            if (comm == null || !"Y".equals(comm.getStat())) return fail(r, "없어진 커뮤니티예요.");

            Optional<TblCommMbr> mine = mbrRepository.findByCommSnAndUserSn(commSn, me);
            if (mine.isPresent() && "ACTIVE".equals(mine.get().getStat())) {
                r.putResult("memberStatus", "ACTIVE");
                r.putResult("commNm", comm.getCommNm());
                r.putResult("already", true);
                r.setResultCode(ResponseCode.SUCCESS.getCode());
                return r;
            }
            // 승인 생략은 만든 사람이 "지금도" 운영진일 때만
            long inviter = ((Number) inv.get("created_by")).longValue();
            boolean inviterManages = activeMember(commSn, inviter).map(InviteService::isManager).orElse(false);
            boolean inviterMember = activeMember(commSn, inviter).isPresent();
            if (!inviterMember) return fail(r, "초대한 사람이 더 이상 이 커뮤니티 멤버가 아니라 링크를 쓸 수 없어요.");

            Map<String, Object> p = new HashMap<>();
            p.put("commSn", commSn);
            p.put("isMember", true);
            p.put("agreeRules", params.get("agreeRules"));
            // 공개 커뮤니티는 어차피 바로 멤버라 초대 경로로 태워 가입 유형만 INVITE 로 남긴다
            ResultVO joined = communityService.setMember(p, me, isOpen(comm) || inviterManages);
            if (joined.getResultCode() == ResponseCode.SUCCESS.getCode()) {
                jdbc.update("UPDATE knowra_community.tbl_comm_invite SET USE_CNT = USE_CNT + 1 WHERE INVITE_CODE = ?", inv.get("invite_code"));
                joined.putResult("commNm", comm.getCommNm());
            }
            return joined;
        } catch (Exception e) {
            ErrorLog.report(e);
            return fail(r, "참여하지 못했어요.");
        }
    }

    /** params: commSn — 내가 이 커뮤니티에 만든 링크를 전부 취소한다. */
    @Transactional
    public ResultVO revoke(Map<String, Object> params, long me) {
        ResultVO r = new ResultVO();
        try {
            long commSn = Long.parseLong(String.valueOf(params.get("commSn")));
            int n = jdbc.update("UPDATE knowra_community.tbl_comm_invite SET REVOKED = TRUE WHERE COMM_SN = ? AND CREATED_BY = ? AND NOT REVOKED", commSn, me);
            r.putResult("revoked", n);
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            fail(r, "링크를 취소하지 못했어요.");
        }
        return r;
    }

    private Map<String, Object> find(String code) {
        if (code == null || !code.matches("[A-Za-z0-9]{6,16}")) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM knowra_community.tbl_comm_invite WHERE INVITE_CODE = ?", code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String invalidReason(Map<String, Object> inv) {
        if (inv == null) return "없는 초대 링크예요. 주소를 다시 확인해 주세요.";
        if (Boolean.TRUE.equals(inv.get("revoked"))) return "초대한 사람이 취소한 링크예요.";
        Object exp = inv.get("expires_at");
        if (exp instanceof Timestamp t && t.toLocalDateTime().isBefore(LocalDateTime.now())) return "기간이 지난 초대 링크예요. 새 링크를 받아 주세요.";
        return null;
    }

    private static Map<String, Object> fileOf(TblComFile f) {
        if (f == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("url", f.getUrl());   // 화면은 url 을 쓴다(저장소 주소)
        m.put("atchFilePathNm", f.getAtchFilePathNm());
        m.put("strgFileNm", f.getStrgFileNm());
        m.put("atchFileExtnNm", f.getAtchFileExtnNm());
        return m;
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private static ResultVO fail(ResultVO r, String msg) {
        r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
        r.setResultMessage(msg);
        return r;
    }
}
