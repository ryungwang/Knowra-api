package com.knowra.chat.service;

import com.knowra.cmm.handler.NotificationWebSocketHandler;
import com.knowra.cmm.i18n.Msg;
import com.knowra.push.service.WebPushService;
import com.knowra.user.entity.TblUser;
import com.knowra.user.entity.TblUserStng;
import com.knowra.user.repository.TblUserRepository;
import com.knowra.user.repository.TblUserStngRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DM·그룹 메시지 웹 푸시.
 *
 * <p>소켓으로 접속 중인 사람에겐 보내지 않는다 — 이미 화면에 배지·대화가 뜬다. 창을 닫아 둔 사람에게만 간다.
 * 알림 목록(tbl_user_notif)에는 남기지 않는다 — 대화마다 알림이 쌓이면 다른 알림이 묻힌다. 안 읽은 수는 대화
 * 목록이 센다.
 *
 * <p>받는 사람의 언어(설정 LANG)로 쓴다 — 보낸 사람이 아니라.
 *
 * <p>커밋 뒤에 보낸다(AFTER_COMMIT) — 저장이 실패한 메시지가 푸시로 먼저 가면 안 되고, 푸시 서버를 기다리느라
 * 보내기가 느려져서도 안 된다.
 */
@Component
@RequiredArgsConstructor
public class ChatPushListener {

    static final int SNIPPET = 80;

    private final NotificationWebSocketHandler wsHandler;
    private final WebPushService webPushService;
    private final TblUserStngRepository stngRepository;
    private final TblUserRepository userRepository;

    @PersistenceContext
    private EntityManager em;

    @Async
    @TransactionalEventListener
    public void onSent(ChatMsgSentEvent e) {
        String nick = userRepository.findById(e.senderSn()).map(TblUser::getNickName).orElse(null);
        for (Map.Entry<Long, Boolean> r : e.recipients().entrySet()) {
            long to = r.getKey();
            try {
                if (wsHandler.isOnline(to)) continue;
                TblUserStng st = stngRepository.findByUserSn(to);
                if ((st != null && "N".equals(st.getMsgNtfctnYn())) || mutedSender(to, e.senderSn())) continue;
                String lang = st == null || st.getLang() == null ? com.knowra.cmm.i18n.Lang.KO : st.getLang();
                webPushService.sendToUser(to, payload(e, nick, r.getValue(), lang));
            } catch (Exception ignore) { /* 푸시는 덤이다 — 한 사람 실패가 다른 사람을 막지 않게 */ }
        }
    }

    /** 숨긴 사람의 메시지는 조용히 — 대화는 그대로 쌓이고 열면 보인다 */
    private boolean mutedSender(long userSn, long senderSn) {
        return !em.createNativeQuery("SELECT 1 FROM knowra_user.tbl_user_mute WHERE user_sn = ?1 AND muted_sn = ?2")
                .setParameter(1, userSn).setParameter(2, senderSn).setMaxResults(1).getResultList().isEmpty();
    }

    /**
     * 푸시 내용. 같은 방의 푸시는 tag 가 같아 쌓이지 않고 마지막 것으로 바뀐다. 누르면 그 대화방이 열린다.
     * 메시지 요청(수락 전)은 내용을 싣지 않는다 — 모르는 사람의 말이 잠금 화면에 그대로 뜨면 안 된다.
     */
    static Map<String, Object> payload(ChatMsgSentEvent e, String nick, boolean request, String lang) {
        String who = nick == null ? Msg.tl(lang, "누군가") : nick;
        String title;
        String body;
        if (request) {
            title = Msg.tl(lang, "메시지 요청");
            body = Msg.tl(lang, "{0}님이 메시지를 보냈어요.", who);
        } else if (e.group()) {
            title = e.roomName() == null || e.roomName().isBlank() ? Msg.tl(lang, "그룹 대화")
                    : e.eventRoom() ? Msg.tl(lang, "모임 · {0}", e.roomName()) : e.roomName();
            body = who + ": " + snippet(e.text(), e.hasImage(), lang);
        } else {
            title = who;
            body = snippet(e.text(), e.hasImage(), lang);
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("title", title);
        p.put("body", body);
        p.put("url", "/?chat=" + e.roomSn());
        p.put("tag", "CHAT:" + e.roomSn());
        return p;
    }

    static String snippet(String text, boolean hasImage, String lang) {
        String t = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) return hasImage ? Msg.tl(lang, "사진을 보냈어요.") : "";
        return t.length() > SNIPPET ? t.substring(0, SNIPPET) + "…" : t;
    }
}
