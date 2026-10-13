package com.knowra.cmm.handler;

import tools.jackson.databind.ObjectMapper;
import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.session.SessionService;
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
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import com.knowra.session.SessionRevokedEvent;
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
    private final RedisApiService redisApiService;
    private final SessionService sessionService;

    private static final int REDIS_DB = 15;          // 로그아웃 토큰 블랙리스트 — JwtAuthenticationFilter 와 같은 DB
    private static final String ATTR_USER = "userSn";
    private static final String ATTR_OPENED = "openedAt";
    private static final String ATTR_SID = "sid";
    private static final long AUTH_TIMEOUT_MS = 10_000;

    @PersistenceContext
    private EntityManager em;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // userSn → 그 사람의 열린 연결들(탭·기기마다 하나). 예전엔 하나만 들고 있어 마지막 탭만 알림을 받았다.
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    // ── 연결 수립 ─────────────────────────────────────────────────────────
    /**
     * ⚠️ <b>토큰을 주소(?token=)로 받지 않는다.</b> 주소는 nginx 접근 로그·프록시 로그에 그대로 남고,
     * 연결이 예외로 닫히면 Spring 이 세션(주소 포함)을 ERROR 로그로 찍는다 — 실제로 한 시간에 1,555번
     * 토큰 전체가 로그에 찍혔다. 연결 뒤 첫 메시지 {@code {"type":"AUTH","token":...}} 로 받는다.
     * 10초 안에 인증하지 않은 연결은 {@link #closeUnauthenticated()} 가 끊는다.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(ATTR_OPENED, System.currentTimeMillis());
        pending.add(session);
    }

    /** HTTP 요청과 같은 기준으로 검증한다 — 서명·만료, access 토큰, 로그아웃(블랙리스트), 다른 기기에서 끊은 세션. */
    private Long authenticate(String token) {
        if (token == null || token.isBlank() || !jwtProvider.isValid(token) || !jwtProvider.isAccessToken(token)) return null;
        if (redisApiService.isBlocklisted(REDIS_DB, token)) return null;
        if (sessionService.isRevoked(jwtProvider.extractSid(token))) return null;
        long userSn = jwtProvider.extractUserSn(token);
        // 정지된 계정(STATUS=S)은 액세스 토큰이 아직 살아 있어도 소켓을 열어 주지 않는다
        String status = new JPAQueryFactory(em).select(QTblUser.tblUser.status).from(QTblUser.tblUser)
                .where(QTblUser.tblUser.userSn.eq(userSn)).fetchOne();
        return "Y".equals(status) ? userSn : null;
    }

    private void onAuth(WebSocketSession session, Object token) throws Exception {
        Long userSn = authenticate(token == null ? null : String.valueOf(token));
        if (userSn == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("auth"));
            return;
        }
        session.getAttributes().put(ATTR_USER, userSn);
        String sid = jwtProvider.extractSid(String.valueOf(token));
        if (sid != null) session.getAttributes().put(ATTR_SID, sid);
        sessions.computeIfAbsent(userSn, k -> ConcurrentHashMap.newKeySet()).add(session);

        List<TblUserNotif> entities = notifService.getAll(userSn);
        long unreadCnt = notifService.countUnread(userSn);

        Map<Long, String[]> senderInfoMap = fetchSenderInfoMap(entities);
        Map<Long, String> commNames = fetchCommNames(entities);

        List<NotifDTO> list = entities.stream()
                .map(e -> {
                    // ⚠️ 보낸 사람이 없는 알림(뱃지·모임 리마인더)은 senderSn 이 null — getOrDefault(null) 이
                    //    불변 Map 에서 NPE 를 던져 연결이 통째로 죽고 재연결을 끝없이 반복했다
                    String[] info = e.getSenderSn() == null ? null : senderInfoMap.get(e.getSenderSn());
                    if (info == null) info = new String[]{null, null};
                    // ⚠️ Map.of().get(null) 은 NPE — 팔로우 알림은 targetSn 이 null 이다(연결이 끊기며 알림이 통째로 안 왔다)
                    String commNm = "COMM".equals(e.getTargetKind()) && e.getTargetSn() != null ? commNames.get(e.getTargetSn()) : null;
                    return new NotifDTO(e, info[0], info[1], commNm);
                })
                .collect(Collectors.toList());

        synchronized (session) {
            send(session, Map.of(
                    "type", "INIT",
                    "data", Map.of("unreadCnt", unreadCnt, "list", list)));
        }
    }

    /** 인증 없이 붙어만 있는 연결을 정리한다. */
    @Scheduled(fixedDelay = 2_000)   // 메모리만 보는 가벼운 검사 — 10초 제한이 최대 12초 안에 지켜진다
    public void closeUnauthenticated() {
        // 세션 목록은 인증된 것만 들고 있으므로, 대기 중인 것은 열린 시각으로 따로 본다
        long now = System.currentTimeMillis();
        pending.removeIf(ws -> {
            if (!ws.isOpen() || ws.getAttributes().get(ATTR_USER) != null) return true;
            Object at = ws.getAttributes().get(ATTR_OPENED);
            if (at instanceof Long t && now - t > AUTH_TIMEOUT_MS) {
                try { ws.close(CloseStatus.POLICY_VIOLATION.withReason("auth timeout")); } catch (Exception ignored) {}
                return true;
            }
            return false;
        });
    }

    private final Set<WebSocketSession> pending = ConcurrentHashMap.newKeySet();

    /** 세션이 끊기면(다른 기기에서 로그아웃·모두 로그아웃·계정 정지) 그 세션으로 열린 소켓을 바로 닫는다. */
    @EventListener
    public void onSessionRevoked(SessionRevokedEvent ev) {
        Set<WebSocketSession> set = sessions.get(ev.userSn());
        if (set == null) return;
        for (WebSocketSession ws : set) {
            if (ev.sid() != null && !ev.sid().equals(ws.getAttributes().get(ATTR_SID))) continue;
            try { ws.close(CloseStatus.POLICY_VIOLATION.withReason("revoked")); } catch (Exception ignored) {}
        }
    }

    // ── 클라이언트 메시지 수신 ────────────────────────────────────────────
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = MAPPER.readValue(message.getPayload(), Map.class);
        String type = String.valueOf(payload.get("type"));

        Long userSn = userOf(session);
        if (userSn == null) {
            // 인증 전엔 AUTH 말고는 받지 않는다
            if ("AUTH".equals(type)) { pending.remove(session); onAuth(session, payload.get("token")); }
            else session.close(CloseStatus.POLICY_VIOLATION.withReason("auth"));
            return;
        }

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
        pending.remove(session);
        Long userSn = userOf(session);
        if (userSn != null) sessions.computeIfPresent(userSn, (k, set) -> { set.remove(session); return set.isEmpty() ? null : set; });
    }

    // ── 실시간 알림 Push (NotificationEventListener에서 호출) ─────────────
    public void push(long userSn, NotifDTO notifDTO) {
        pushEvent(userSn, "NOTIFICATION", notifDTO);
    }

    /**
     * 알림이 아닌 실시간 이벤트(채팅 메시지 등)도 같은 소켓으로 보낸다 — 소켓을 둘 열 이유가 없다.
     * 프론트는 {@code type} 으로 분기한다. 상대가 접속 중이 아니면 조용히 버린다(DB 에는 이미 있다).
     */
    public void pushEvent(long userSn, String type, Object data) {
        Set<WebSocketSession> set = sessions.get(userSn);
        if (set == null) return;
        for (WebSocketSession ws : set) {
            if (!ws.isOpen()) continue;
            try {
                // 한 연결에 두 스레드가 동시에 쓰면 IllegalStateException — 연결 단위로 잠근다
                synchronized (ws) { send(ws, Map.of("type", type, "data", data)); }
            } catch (Exception e) {
                log.warn("WebSocket push 실패 userSn={} type={} : {}", userSn, type, e.getMessage());
            }
        }
    }

    public boolean isOnline(long userSn) {
        Set<WebSocketSession> set = sessions.get(userSn);
        return set != null && set.stream().anyMatch(WebSocketSession::isOpen);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────
    private void send(WebSocketSession session, Object payload) throws Exception {
        session.sendMessage(new TextMessage(MAPPER.writeValueAsString(payload)));
    }

    /** 인증 때 붙여 둔 사용자. 토큰을 매번 다시 풀지 않는다 — 접속 중에 토큰이 만료돼도 연결은 유지된다. */
    private static Long userOf(WebSocketSession session) {
        return session.getAttributes().get(ATTR_USER) instanceof Long l ? l : null;
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
        if (senderSns.isEmpty()) return new HashMap<>();   // 불변 Map.of() 는 get(null) 에서 NPE — 호출부가 null 키로 찾는다

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
