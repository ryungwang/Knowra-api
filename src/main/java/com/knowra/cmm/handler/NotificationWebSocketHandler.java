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

        List<NotifDTO> list = entities.stream()
                .map(e -> {
                    String[] info = senderInfoMap.getOrDefault(e.getSenderSn(), new String[]{null, null});
                    return new NotifDTO(e, info[0], info[1]);
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

    private Map<Long, String[]> fetchSenderInfoMap(List<TblUserNotif> entities) {
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
