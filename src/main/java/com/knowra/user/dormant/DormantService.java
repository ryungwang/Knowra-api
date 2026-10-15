package com.knowra.user.dormant;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.messaging.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import com.knowra.cmm.i18n.Msg;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 휴면 계정 — 오래 안 들어온 계정의 로그인 상태를 끊고 메일·푸시를 멈춘다. 로그인하면 바로 풀린다(AuthService).
 *
 * <p>휴면은 STATUS='D' 다. 주간 소식·사람 찾기·멘션 추천은 원래 STATUS='Y' 만 보므로 따로 손대지 않아도 빠진다.
 * 정지(S)·탈퇴 계정은 건드리지 않는다 — 정지가 끝날 때 휴면으로 덮이면 「왜 휴면이냐」가 된다.
 *
 * <p>시각은 전부 JVM 에서 넘긴다 — 저장된 시각이 JVM(한국) 시각이고 DB 의 now() 는 UTC 다.
 * 안내 메일은 「안내함」 칸을 먼저 조건부로 채운 사람에게만 보낸다 — 서버가 여러 대여도 한 번만 간다.
 */
@Slf4j
@Service
public class DormantService {

    /** 마지막 접속 — 로그인 기록·기기 세션 중 늦은 쪽, 둘 다 없으면 가입일 */
    private static final String LAST_SEEN = """
            GREATEST(COALESCE(u.frst_crt_dt, u.join_ymd),
                     (SELECT MAX(h.lgn_dt) FROM knowra_user.tbl_user_lgn_hstry h WHERE h.user_sn = u.user_sn),
                     (SELECT MAX(s.last_seen_at) FROM knowra_user.tbl_user_session s WHERE s.user_sn = u.user_sn))""";

    /** 메일 주소 꼴인가 — 옛 시드의 'deer' 같은 값은 보낼 수 없다(주간 소식과 같은 기준) */
    private static final String MAILABLE = "COALESCE(u.email, '') LIKE '%_@_%.__%'";

    private final JdbcTemplate jdbc;
    private final MailService mail;
    private final int days, noticeDays;
    private final String front;

    public DormantService(JdbcTemplate jdbc, MailService mail,
                          @Value("${knowra.dormant.days:365}") int days,
                          @Value("${knowra.dormant.notice-days:30}") int noticeDays,
                          @Value("${knowra.front.base-url:http://localhost:5111}") String front) {
        this.jdbc = jdbc; this.mail = mail; this.days = days; this.noticeDays = noticeDays;
        this.front = front.replaceAll("/+$", "");
    }

    @Scheduled(cron = "${knowra.dormant.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void daily() {
        try { log.info("[DORMANT] {}", run(LocalDateTime.now())); }
        catch (Exception e) { ErrorLog.report(e); }
    }

    public Map<String, Integer> run(LocalDateTime now) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("noticed", notice(now));
        out.put("dormant", makeDormant(now));
        return out;
    }

    /**
     * 휴면 noticeDays 일 전 — 한 번만. 아래쪽 경계를 두지 않는다: 이 기능이 처음 켜지는 날 이미 1년이 넘은 사람도
     * 알림부터 받고 noticeDays 뒤에 휴면이 된다(알리지 않고 바로 끊지 않는다).
     */
    private int notice(LocalDateTime now) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.user_sn, u.email, u.name, st.lang, %s AS last_seen FROM knowra_user.tbl_user u
                LEFT JOIN knowra_user.tbl_user_stng st ON st.user_sn = u.user_sn
                WHERE u.status = 'Y' AND u.actvtn_yn = 'Y' AND u.dormant_notice_at IS NULL
                  AND %s""".formatted(LAST_SEEN, MAILABLE) + " AND " + LAST_SEEN + " < ?", now.minusDays(days - noticeDays));
        int sent = 0;
        for (Map<String, Object> r : rows) {
            long sn = ((Number) r.get("user_sn")).longValue();
            // 먼저 표시한 사람만 보낸다 — 다른 서버가 같은 사람을 집었으면 0줄
            if (jdbc.update("UPDATE knowra_user.tbl_user SET dormant_notice_at = ? WHERE user_sn = ? AND dormant_notice_at IS NULL", now, sn) == 0) continue;
            LocalDateTime lastSeen = ((java.sql.Timestamp) r.get("last_seen")).toLocalDateTime();
            try {
                LocalDateTime on = lastSeen.plusDays(days), earliest = now.plusDays(noticeDays);
                String lang = r.get("lang") == null ? "ko" : String.valueOf(r.get("lang"));
                mail.send(String.valueOf(r.get("email")), Msg.tl(lang, "[knowra] 곧 휴면 계정으로 바뀌어요"), body(String.valueOf(r.get("name")), on.isBefore(earliest) ? earliest : on, lang));
                sent++;
            } catch (RuntimeException e) {
                // 못 보냈으면 표시를 되돌려 다음 날 다시
                jdbc.update("UPDATE knowra_user.tbl_user SET dormant_notice_at = NULL WHERE user_sn = ?", sn);
                log.warn("[DORMANT] userSn={} 안내 메일 실패: {}", sn, e.getMessage());
            }
        }
        return sent;
    }

    private int makeDormant(LocalDateTime now) {
        // 알린 지 noticeDays 가 지났거나, 메일 주소가 없어 알릴 길이 없는 사람만
        List<Long> sns = jdbc.queryForList("SELECT u.user_sn FROM knowra_user.tbl_user u WHERE u.status = 'Y' AND u.actvtn_yn = 'Y' AND "
                + LAST_SEEN + " < ? AND (u.dormant_notice_at <= ? OR NOT " + MAILABLE + ")", Long.class, now.minusDays(days), now.minusDays(noticeDays));
        int n = 0;
        for (Long sn : sns) {
            if (jdbc.update("UPDATE knowra_user.tbl_user SET status = 'D', dormant_at = ? WHERE user_sn = ? AND status = 'Y'", now, sn) == 0) continue;
            // 남은 기기 로그인은 끊는다(리프레시는 세션이 있어야 산다). 푸시 구독도 — 1년 묵은 기기에 알림을 보내지 않는다
            jdbc.update("DELETE FROM knowra_user.tbl_user_session WHERE user_sn = ?", sn);
            jdbc.update("DELETE FROM knowra_user.tbl_push_sub WHERE user_sn = ?", sn);
            n++;
        }
        return n;
    }

    /**
     * 로그인 성공 때 — 휴면이면 풀고 true. 안내 표시도 지운다(다시 1년이 지나면 또 알린다).
     * 비밀번호·소셜·2단계 로그인이 모두 지나는 자리에서 부른다.
     */
    public boolean wake(long userSn) {
        jdbc.update("UPDATE knowra_user.tbl_user SET dormant_notice_at = NULL WHERE user_sn = ? AND dormant_notice_at IS NOT NULL", userSn);
        return jdbc.update("UPDATE knowra_user.tbl_user SET status = 'Y', dormant_at = NULL WHERE user_sn = ? AND status = 'D'", userSn) > 0;
    }

    private String body(String name, LocalDateTime on, String lang) {
        // 날짜만 — 그 언어의 긴 날짜 꼴(「2026년 10월 18일」 / 「October 18, 2026」)
        String date = on.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(com.knowra.cmm.i18n.Fmt.locale(lang)));
        return Msg.tl(lang, "{0}님, 안녕하세요. knowra 입니다.\n\n마지막으로 들어오신 지 1년이 다 되어 가요. {1}에 휴면 계정으로 바뀝니다.\n\n휴면이 되면\n- 모든 기기에서 로그인이 풀리고\n- 알림·소식 메일과 푸시가 멈추고\n- 사람 찾기와 멘션 추천에서 빠져요.\n쓰신 글과 댓글은 그대로 남아요.\n\n계속 쓰시려면 그 전에 한 번 들어와 주세요. 휴면이 된 뒤에도 로그인하면 바로 풀려요.\n{2}\n",
                name == null || name.isBlank() ? Msg.tl(lang, "회원") : name, date, front);
    }
}
