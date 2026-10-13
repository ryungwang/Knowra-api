package com.knowra.cmm.handler;

import tools.jackson.databind.ObjectMapper;
import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.common.entity.QTblComFile;
import com.knowra.user.dto.NotifDTO;
import com.knowra.user.entity.QTblUser;
import com.knowra.user.entity.TblUserNotif;
import com.knowra.user.service.NotifService;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final JwtProvider jwtProvider;
    private final NotifService notifService;

    @PersistenceContext
    private EntityManager em;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // userSn → WebSocket 세션
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // ── 연결 수립 ─────────────────────────────────────────────────────────
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userSn = extractUserSn(session);
        if (userSn == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        sessions.put(userSn, session);

        List<TblUserNotif> entities = notifService.getAll(userSn);
        long unreadCnt = notifService.countUnread(userSn);

        Map<Long, String[]> senderInfoMap = fetchSenderInfoMap(entities);
        Map<Long, String> commNames = fetchCommNames(entities);

        List<NotifDTO> list = entities.stream()
                .map(e -> {
                    String[] info = senderInfoMap.getOrDefault(e.getSenderSn(), new String[]{null, null});
                    // ⚠️ Map.of().get(null) 은 NPE — 팔로우 알림은 targetSn 이 null 이다(연결이 끊기며 알림이 통째로 안 왔다)
                    String commNm = "COMM".equals(e.getTargetKind()) && e.getTargetSn() != null ? commNames.get(e.getTargetSn()) : null;
                    return new NotifDTO(e, info[0], info[1], commNm);
                })
                .collect(Collectors.toList());

        send(session, Map.of(
                "type", "INIT",
                "data", Map.of("unreadCnt", unreadCnt, "list", list)));
    }

    // ── 클라이언트 메시지 수신 ────────────────────────────────────────────
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userSn = extractUserSn(session);
        if (userSn == null) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = MAPPER.readValue(message.getPayload(), Map.class);
        String type = String.valueOf(payload.get("type"));

        switch (type) {
            case "MARK_READ" -> {
                long notifSn = Long.parseLong(String.valueOf(payload.get("notifSn")));
                notifService.markRead(notifSn, userSn);
            }
            case "MARK_ALL_READ" -> notifService.markAllRead(userSn);
            case "DISMISS" -> {
                long notifSn = Long.parseLong(String.valueOf(payload.get("notifSn")));
                notifService.dismiss(notifSn, userSn);
            }
            case "DISMISS_ALL" -> notifService.dismissAll(userSn);
        }
    }

    // ── 연결 종료 ─────────────────────────────────────────────────────────
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userSn = extractUserSn(session);
        if (userSn != null) sessions.remove(userSn);
    }

    // ── 실시간 알림 Push (NotificationEventListener에서 호출) ─────────────
    public void push(long userSn, NotifDTO notifDTO) {
        WebSocketSession session = sessions.get(userSn);
        if (session == null || !session.isOpen()) return;
        try {
            send(session, Map.of("type", "NOTIFICATION", "data", notifDTO));
        } catch (Exception e) {
            log.warn("WebSocket push 실패 userSn={}", userSn, e);
        }
    }

    /**
     * 알림이 아닌 실시간 이벤트(채팅 메시지 등)도 같은 소켓으로 보낸다 — 소켓을 둘 열 이유가 없다.
     * 프론트는 {@code type} 으로 분기한다. 상대가 접속 중이 아니면 조용히 버린다(DB 에는 이미 있다).
     */
    public void pushEvent(long userSn, String type, Object data) {
        WebSocketSession session = sessions.get(userSn);
        if (session == null || !session.isOpen()) return;
        try {
            send(session, Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.warn("WebSocket pushEvent 실패 userSn={} type={}", userSn, type, e);
        }
    }

    public boolean isOnline(long userSn) {
        WebSocketSession s = sessions.get(userSn);
        return s != null && s.isOpen();
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────
    private void send(WebSocketSession session, Object payload) throws Exception {
        session.sendMessage(new TextMessage(MAPPER.writeValueAsString(payload)));
    }

    private Long extractUserSn(WebSocketSession session) {
        try {
            String query = session.getUri() != null ? session.getUri().getQuery() : null;
            if (query == null) return null;
            return Arrays.stream(query.split("&"))
                    .filter(p -> p.startsWith("token="))
                    .map(p -> p.substring(6))
                    .findFirst()
                    .map(jwtProvider::extractUserSn)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** COMM 알림의 commSn → 커뮤니티 주소 이름. 알림 링크용. */
    public Map<Long, String> fetchCommNames(List<TblUserNotif> entities) {
        List<Long> sns = entities.stream().filter(e -> "COMM".equals(e.getTargetKind()) && e.getTargetSn() != null)
                .map(TblUserNotif::getTargetSn).distinct().collect(Collectors.toList());
        if (sns.isEmpty()) return Map.of();
        com.knowra.community.entity.QTblComm c = com.knowra.community.entity.QTblComm.tblComm;
        Map<Long, String> out = new HashMap<>();
        new JPAQueryFactory(em).select(c.commSn, c.commNm).from(c).where(c.commSn.in(sns)).fetch()
                .forEach(t -> out.put(t.get(c.commSn), t.get(c.commNm)));
        return out;
    }

    /** 보낸 사람 닉네임·프로필 사진 — 알림 페이지(NotifController)도 같이 쓴다. */
    public Map<Long, String[]> fetchSenderInfoMap(List<TblUserNotif> entities) {
        List<Long> senderSns = entities.stream()
                .map(TblUserNotif::getSenderSn)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (senderSns.isEmpty()) return Map.of();

        QTblUser    qUser   = QTblUser.tblUser;
        QTblComFile pfpFile = new QTblComFile("pfpFile");

        Map<Long, String[]> result = new HashMap<>();
        new JPAQueryFactory(em)
                .select(qUser.userSn, qUser.nickName,
                        pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                .from(qUser)
                .leftJoin(qUser.pfp, pfpFile)
                .where(qUser.userSn.in(senderSns))
                .fetch()
                .forEach(t -> {
                    String pathNm = t.get(pfpFile.atchFilePathNm);
                    String pfpUrl = pathNm != null
                            ? pathNm + "/" + t.get(pfpFile.strgFileNm) + "." + t.get(pfpFile.atchFileExtnNm)
                            : null;
                    result.put(t.get(qUser.userSn), new String[]{t.get(qUser.nickName), pfpUrl});
                });
        return result;
    }
}
