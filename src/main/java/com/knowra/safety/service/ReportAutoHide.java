package com.knowra.safety.service;

import com.knowra.cmm.event.NotifTriggerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 신고 누적 자동 숨김 — 서로 다른 사람 N명(기본 5)이 같은 글·댓글을 신고하면 사람이 보기 전에 먼저 가린다.
 *
 * <p>가리는 방법은 상태를 REVIEW 로 바꾸는 것뿐이다. 목록·피드·검색·사이트맵은 전부 stat='ACTIVE' 만 보므로
 * 따로 손대지 않아도 빠진다. 글은 쓴 사람만 열 수 있고(「검토 중」 안내), 댓글은 자리만 남고 내용은 쓴 사람에게만.
 * 사이트 관리자나 그 커뮤니티 운영진이 「문제없음」으로 닫으면 되돌린다 — 몰려와서 신고로 남의 글을 지우는 걸 막는 길.
 */
@Service
@RequiredArgsConstructor
public class ReportAutoHide {

    public static final String REVIEW = "REVIEW";

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher publisher;

    @Value("${knowra.report.auto-hide-threshold:5}")
    private int threshold;

    /** 사이트 관리자 — 지금은 1번 회원뿐이다(JwtProvider 와 같은 기준). 아동 안전 신고를 바로 알린다 */
    static final long SITE_ADMIN_SN = 1L;

    /** 신고 한 건이 들어온 뒤 부른다. 이번에 가렸으면 true */
    public boolean afterReport(String kind, long targetSn) { return afterReport(kind, targetSn, null); }

    /**
     * 아동 안전(CHILD_SAFETY) 신고는 문턱 없이 한 건으로 바로 가린다 — 확인을 기다리는 동안 퍼지면 되돌릴 수 없다.
     * 지우지 않고 가린다: 수사기관 신고·증거 보존을 위해 원본이 남아 있어야 한다. 글쓴이에게는 알리지 않는다(증거 인멸·경고가 된다).
     * 관리자에게 바로 알림을 보낸다. 메시지(MSG)도 바로 가린다.
     */
    public boolean afterReport(String kind, long targetSn, String reason) {
        boolean child = "CHILD_SAFETY".equals(reason);
        if (child) publisher.publishEvent(new NotifTriggerEvent(SITE_ADMIN_SN, null, "CHILD_SAFETY_REPORT", null, "ADMIN", kind));
        if (child && "MSG".equals(kind))
            return jdbc.update("UPDATE knowra_user.tbl_chat_msg SET hidden_at = now() WHERE msg_sn = ? AND hidden_at IS NULL", targetSn) == 1;
        if (!"POST".equals(kind) && !"CMT".equals(kind)) return false;
        Long reporters = jdbc.queryForObject("SELECT COUNT(DISTINCT reporter_sn) FROM knowra_user.tbl_report WHERE target_kind = ? AND target_sn = ? AND stat = 'OPEN'",
                Long.class, kind, targetSn);
        if (!child && (reporters == null || reporters < threshold)) return false;
        String table = "POST".equals(kind) ? "knowra_post.tbl_post" : "knowra_post.tbl_post_cmt";
        String key = "POST".equals(kind) ? "post_sn" : "post_cmt_sn";
        // ACTIVE 일 때만 — 예약 글·지운 댓글·이미 가린 것은 그대로
        int n = jdbc.update("UPDATE " + table + " SET stat = '" + REVIEW + "' WHERE " + key + " = ? AND stat = 'ACTIVE' AND actvtn_yn = 'Y'", targetSn);
        if (n == 0) return false;
        if (child) return true;   // 아동 안전 — 글쓴이에게 알리지 않는다
        // 쓴 사람에게 알린다 — 왜 안 보이는지 모르면 문의가 된다. 알림은 그 글로 이어진다(쓴 사람은 열 수 있다)
        var row = jdbc.queryForMap("POST".equals(kind)
                ? "SELECT user_sn, post_sn FROM knowra_post.tbl_post WHERE post_sn = ?"
                : "SELECT user_sn, post_sn FROM knowra_post.tbl_post_cmt WHERE post_cmt_sn = ?", targetSn);
        publisher.publishEvent(new NotifTriggerEvent(((Number) row.get("user_sn")).longValue(), null, "REPORT_HIDDEN",
                ((Number) row.get("post_sn")).longValue(), "POST", "POST".equals(kind) ? "글" : "댓글"));
        return true;
    }

    /** 「문제없음」 — 가려 둔 게 있으면 되돌린다 */
    public boolean restore(String kind, long targetSn) {
        if ("POST".equals(kind)) return jdbc.update("UPDATE knowra_post.tbl_post SET stat = 'ACTIVE' WHERE post_sn = ? AND stat = ?", targetSn, REVIEW) == 1;
        if ("CMT".equals(kind)) return jdbc.update("UPDATE knowra_post.tbl_post_cmt SET stat = 'ACTIVE' WHERE post_cmt_sn = ? AND stat = ?", targetSn, REVIEW) == 1;
        return false;
    }
}
