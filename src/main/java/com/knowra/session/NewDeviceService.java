package com.knowra.session;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.cmm.messaging.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import com.knowra.cmm.i18n.Fmt;
import com.knowra.cmm.i18n.Msg;
import java.util.List;

/**
 * 새 기기 로그인 알림 — 처음 보는 기기(「Chrome · Windows」)로 로그인하면 알림·푸시·메일로 알린다.
 *
 * <p>처음 세는 계정은 조용히 기억만 한다: 이 기능이 켜진 날 모든 사람에게 「새 기기」가 쏟아지면 진짜 경고가 묻힌다.
 * 그때 살아 있는 다른 기기 세션이 있으면 그것들도 아는 기기로 넣어, 그 밖의 기기면 첫 번째부터 알린다.
 *
 * <p>로그인 응답을 메일 발송이 붙잡지 않게 비동기로 돈다.
 */
@Slf4j
@Service
public class NewDeviceService {

    private final JdbcTemplate jdbc;
    private final MailService mail;
    private final ApplicationEventPublisher events;
    private final String front;

    public NewDeviceService(JdbcTemplate jdbc, MailService mail, ApplicationEventPublisher events,
                            @Value("${knowra.front.base-url:http://localhost:5111}") String front) {
        this.jdbc = jdbc; this.mail = mail; this.events = events;
        this.front = front.replaceAll("/+$", "");
    }

    @Async
    public void onLogin(long userSn, String userAgent, String ip, String sid) {
        try {
            check(userSn, userAgent, ip, sid, LocalDateTime.now());
        } catch (Exception e) {
            ErrorLog.report(e);   // 알림이 실패해도 로그인은 이미 끝났다
        }
    }

    /** 새 기기라 알렸으면 true */
    boolean check(long userSn, String userAgent, String ip, String sid, LocalDateTime now) {
        String device = SessionService.describe(userAgent);
        if (jdbc.update("UPDATE knowra_user.tbl_known_device SET last_seen = ? WHERE user_sn = ? AND device = ?", now, userSn, device) > 0) return false;

        boolean firstTime = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_known_device WHERE user_sn = ?", Long.class, userSn) == 0;
        if (firstTime) {
            // 지금 로그인 말고 이미 살아 있던 기기들 — 이것들은 본인 것으로 본다
            List<String> others = jdbc.queryForList("SELECT user_agent FROM knowra_user.tbl_user_session WHERE user_sn = ? AND sid <> ?", String.class, userSn, sid == null ? "" : sid);
            for (String ua : others) remember(userSn, SessionService.describe(ua), now);
            boolean known = others.stream().map(SessionService::describe).anyMatch(device::equals);
            remember(userSn, device, now);
            if (known || others.isEmpty()) return false;
        } else if (!remember(userSn, device, now)) {
            return false;   // 동시에 들어온 다른 요청이 먼저 넣었다 — 한 번만 알린다
        }
        alert(userSn, device, SessionService.maskIp(ip), now);
        return true;
    }

    private boolean remember(long userSn, String device, LocalDateTime now) {
        return jdbc.update("INSERT INTO knowra_user.tbl_known_device (user_sn, device, first_seen, last_seen) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                userSn, device, now, now) > 0;
    }

    private void alert(long userSn, String device, String maskedIp, LocalDateTime now) {
        // 받는 사람의 언어·시간대 — 설정 줄이 없으면 한국어·서울
        var pref = jdbc.queryForList("SELECT lang, time_zone FROM knowra_user.tbl_user_stng WHERE user_sn = ?", userSn);
        String lang = pref.isEmpty() || pref.get(0).get("lang") == null ? "ko" : String.valueOf(pref.get(0).get("lang"));
        String tz = pref.isEmpty() || pref.get(0).get("time_zone") == null ? null : String.valueOf(pref.get(0).get("time_zone"));
        String shown = SessionService.label(device, lang);
        String where = maskedIp == null ? shown : shown + " (" + maskedIp + ")";
        events.publishEvent(new NotifTriggerEvent(userSn, null, "NEW_LOGIN", null, null, where));
        var row = jdbc.queryForList("SELECT email, name FROM knowra_user.tbl_user WHERE user_sn = ? AND COALESCE(email, '') LIKE '%_@_%.__%'", userSn);
        if (row.isEmpty()) return;
        try {
            mail.send(String.valueOf(row.get(0).get("email")), Msg.tl(lang, "[knowra] 새 기기에서 로그인했어요"),
                    Msg.tl(lang, "{0}님, 방금 처음 보는 기기에서 knowra 에 로그인했어요.\n\n기기: {1}\n위치(IP 대역): {2}\n시각: {3}\n\n본인이라면 이 메일은 넘겨도 돼요.\n본인이 아니라면 비밀번호를 바꾸고, 설정 > 보안 > 로그인한 기기에서 그 기기를 로그아웃해 주세요.\n{4}/user/settings\n",
                            String.valueOf(row.get(0).get("name")), shown, maskedIp == null ? Msg.tl(lang, "알 수 없음") : maskedIp, Fmt.dateTime(now, lang, tz), front));
        } catch (RuntimeException e) {
            log.warn("[NEW_LOGIN] userSn={} 메일 실패: {}", userSn, e.getMessage());
        }
    }
}
