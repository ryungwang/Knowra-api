package com.knowra.cmm.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 메일 반송·스팸 신고 주소(V67). SES 알림({@link MailFeedbackController})이 채우고 {@link MailService} 가 보기 전에 본다.
 * <ul>
 *   <li>BOUNCE — 본인이 요청한 메일 말고는 보내지 않는다.</li>
 *   <li>COMPLAINT — 대량 메일(주간 소식)만 보내지 않는다. 보안·계정 안내는 계속 간다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class MailSuppression {
    public static final String BOUNCE = "BOUNCE", COMPLAINT = "COMPLAINT";

    private final JdbcTemplate jdbc;

    static String key(String email) { return email == null ? "" : email.strip().toLowerCase(Locale.ROOT); }

    /** 이 주소로 보내면 안 되는 까닭. 보내도 되면 null. bulk = 주간 소식처럼 한꺼번에 보내는 메일 */
    public String blocked(String email, boolean bulk) {
        String r = jdbc.query("SELECT reason FROM knowra_com.tbl_mail_suppression WHERE email = ?", rs -> rs.next() ? rs.getString(1) : null, key(email));
        if (r == null) return null;
        return BOUNCE.equals(r) || bulk ? r : null;
    }

    /** 영구 반송이 스팸 신고보다 무겁다 — 이미 BOUNCE 면 COMPLAINT 로 낮추지 않는다 */
    public void add(String email, String reason, String detail) {
        String d = detail == null ? null : (detail.length() > 300 ? detail.substring(0, 300) : detail);
        jdbc.update("""
                INSERT INTO knowra_com.tbl_mail_suppression (email, reason, detail) VALUES (?, ?, ?)
                ON CONFLICT (email) DO UPDATE SET reason = CASE WHEN tbl_mail_suppression.reason = 'BOUNCE' THEN 'BOUNCE' ELSE EXCLUDED.reason END,
                                                  detail = EXCLUDED.detail, created_at = now()""", key(email), reason, d);
        // 스팸 신고 = 그만 받고 싶다 — 주간 소식 설정도 끈다(화면에도 꺼진 것으로 보인다)
        if (COMPLAINT.equals(reason))
            jdbc.update("""
                    UPDATE knowra_user.tbl_user_stng s SET digest_yn = 'N' FROM knowra_user.tbl_user u
                    WHERE u.user_sn = s.user_sn AND lower(u.email) = ? AND s.digest_yn <> 'N'""", key(email));
    }

    /** 그 주소로 메일을 받았다는 증거가 생겼다(인증 코드 확인·재설정 링크 사용) — 반송 기록을 지운다 */
    public void clear(String email) {
        jdbc.update("DELETE FROM knowra_com.tbl_mail_suppression WHERE email = ?", key(email));
    }

    /** 본인이 주간 소식을 다시 켰다 — 스팸 신고 기록만 지운다(반송은 그대로) */
    public void clearComplaint(long userSn) {
        jdbc.update("""
                DELETE FROM knowra_com.tbl_mail_suppression m USING knowra_user.tbl_user u
                WHERE u.user_sn = ? AND m.email = lower(u.email) AND m.reason = 'COMPLAINT'""", userSn);
    }
}
