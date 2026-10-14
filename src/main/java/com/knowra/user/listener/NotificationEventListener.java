package com.knowra.user.listener;

import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.cmm.handler.NotificationWebSocketHandler;
import com.knowra.common.entity.QTblComFile;
import com.knowra.user.dto.NotifDTO;
import com.knowra.user.entity.QTblUser;
import com.knowra.user.entity.TblUserNotif;
import com.knowra.user.repository.TblUserNotifRepository;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final TblUserNotifRepository notifRepository;
    private final NotificationWebSocketHandler wsHandler;
    private final com.knowra.safety.service.SafetyService safetyService;
    private final com.knowra.push.service.WebPushService webPushService;
    private final com.knowra.user.repository.TblUserStngRepository stngRepository;
    private final com.knowra.community.repository.TblCommRepository commRepository;

    @PersistenceContext
    private EntityManager em;

    private static final java.util.Set<String> MUTABLE = java.util.Set.of("COMMENT", "REPLY", "LIKE");

    @Async
    @EventListener
    @Transactional
    public void onNotifTrigger(NotifTriggerEvent event) {
        // 자기 자신에게는 알림 미발송
        if (event.senderSn() != null && event.receiverSn() == event.senderSn()) return;
        // 차단 관계면 양쪽 다 조용히 — 차단한 사람의 반응이 알림으로 새면 차단의 의미가 없다
        if (event.senderSn() != null && safetyService.blockedEitherWay(event.receiverSn(), event.senderSn())) return;
        // 숨긴 사람이 한 일(좋아요·댓글·멘션·팔로우 등)은 알림으로 오지 않는다 — 숨기기는 한쪽만이라 상대는 모른다
        if (event.senderSn() != null && !em.createNativeQuery("SELECT 1 FROM knowra_user.tbl_user_mute WHERE user_sn = ?1 AND muted_sn = ?2")
                .setParameter(1, event.receiverSn()).setParameter(2, event.senderSn()).setMaxResults(1).getResultList().isEmpty()) return;
        // 이 글 알림을 끈 사람 — 댓글·답글·좋아요만 조용히(멘션은 나를 직접 부른 것이라 보낸다)
        if (MUTABLE.contains(event.notifTyp()) && "POST".equals(event.targetKind()) && event.targetSn() != null
                && !em.createNativeQuery("SELECT 1 FROM knowra_post.tbl_post_mute WHERE user_sn = ?1 AND post_sn = ?2")
                      .setParameter(1, event.receiverSn()).setParameter(2, event.targetSn()).setMaxResults(1).getResultList().isEmpty()) return;

        // 발신자 닉네임·프로필 조회
        String nickName = null;
        String pfpUrl   = null;
        if (event.senderSn() != null) {
            QTblUser    qUser   = QTblUser.tblUser;
            QTblComFile pfpFile = new QTblComFile("pfpFile");
            Tuple t = new JPAQueryFactory(em)
                    .select(qUser.nickName,
                            pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm, pfpFile.fileKey)
                    .from(qUser)
                    .leftJoin(qUser.pfp, pfpFile)
                    .where(qUser.userSn.eq(event.senderSn()))
                    .fetchOne();
            if (t != null) {
                nickName      = t.get(qUser.nickName);
                String pathNm = t.get(pfpFile.atchFilePathNm);
                pfpUrl        = com.knowra.cmm.storage.StorageUrls.of(pathNm, t.get(pfpFile.strgFileNm), t.get(pfpFile.atchFileExtnNm), t.get(pfpFile.fileKey));
            }
        }

        // 익명 커뮤니티 글에 대한 알림(좋아요·댓글·답글·멘션·새 글)은 보낸 사람을 숨긴다 — 알림이 익명을 깨면 안 된다
        boolean anonymous = event.senderSn() != null && "POST".equals(event.targetKind()) && event.targetSn() != null
                && !em.createQuery("select c.commSn from TblPost p, TblComm c where p.postSn = :sn and c.commSn = p.commSn and c.prvcyStng = 'anonymous'")
                      .setParameter("sn", event.targetSn()).setMaxResults(1).getResultList().isEmpty();
        if (anonymous) { nickName = com.knowra.post.service.PostQuery.ANONYMOUS_NAME; pfpUrl = null; }

        String message = buildMessage(event.notifTyp(), nickName, event.extra());

        TblUserNotif entity = TblUserNotif.builder()
                .userSn(event.receiverSn())
                .senderSn(anonymous ? null : event.senderSn())
                .notifTyp(event.notifTyp())
                .message(message)
                .targetSn(event.targetSn())
                .targetKind(event.targetKind())
                .build();
        notifRepository.save(entity);

        String commNm = null;
        if ("COMM".equals(event.targetKind()) && event.targetSn() != null) {
            com.knowra.community.entity.TblComm c = commRepository.findByCommSn(event.targetSn());
            if (c != null) commNm = c.getCommNm();
        }
        wsHandler.push(event.receiverSn(), new NotifDTO(entity, nickName, pfpUrl, commNm));

        // 웹 푸시 — 설정의 알림 종류 스위치를 따른다. 실패해도 인앱 알림은 이미 저장됐다.
        if (wantsPush(event.receiverSn(), event.notifTyp())) {
            try {
                java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("title", "knowra");
                payload.put("body", message);
                payload.put("url", linkFor(event, anonymous ? null : nickName));
                payload.put("tag", event.notifTyp() + ":" + event.targetSn());   // 같은 글의 좋아요가 쌓이지 않고 바뀐다
                webPushService.sendToUser(event.receiverSn(), payload);
            } catch (Exception ignore) { /* 푸시는 덤이다 */ }
        }
    }

    /** 알림 설정: 좋아요=like, 댓글·답글=cmt, 팔로우=flwr, 나머지(커뮤니티 소식)=sys. 설정 행이 없으면 기본값. */
    private boolean wantsPush(long userSn, String typ) {
        com.knowra.user.entity.TblUserStng st = stngRepository.findByUserSn(userSn);
        if ("NEW_LOGIN".equals(typ)) return true;   // 보안 알림은 설정과 상관없이 보낸다
        if (st == null) return !"LIKE".equals(typ);
        String yn = switch (typ) {
            case "LIKE" -> st.getLikeNtfctnYn();
            case "COMMENT", "REPLY", "MENTION" -> st.getCmtNtfctnYn();
            case "SERIES_NEW", "KEYWORD" -> st.getSysNtfctnYn();
            case "FOLLOW" -> st.getFlwrNtfctnYn();
            default -> st.getSysNtfctnYn();   // 모임(EVENT_*)도 커뮤니티 소식 쪽
        };
        return "Y".equals(yn);
    }

    /** 알림을 눌렀을 때 갈 곳 — 헤더 알림 목록과 같은 규칙. */
    private String linkFor(NotifTriggerEvent e, String senderNick) {
        if ("NEW_LOGIN".equals(e.notifTyp())) return "/user/settings?tab=security";
        if ("POST".equals(e.targetKind()) && e.targetSn() != null) return "/posts/" + e.targetSn();
        if ("FOLLOW".equals(e.notifTyp()) && senderNick != null) return "/user/" + senderNick;
        if ("COMM".equals(e.targetKind()) && e.targetSn() != null) {
            com.knowra.community.entity.TblComm c = commRepository.findByCommSn(e.targetSn());
            if (c != null) return "/community/" + c.getCommNm();
        }
        return "/";
    }

    private String buildMessage(String notifTyp, String nickName, String extra) {
        String sender = nickName != null ? nickName + "님" : "누군가";
        String place  = extra != null ? extra : "커뮤니티";
        String msg = switch (notifTyp) {
            case "LIKE"        -> sender + "이 내 게시글을 좋아합니다.";
            case "COMMENT"     -> sender + "이 내 게시글에 댓글을 달았습니다.";
            case "REPLY"       -> sender + "이 내 댓글에 답글을 달았습니다.";
            case "MENTION"     -> sender + "이 회원님을 언급했습니다.";
            case "EVENT_NEW"      -> { String[] x = (extra == null ? "|" : extra).split("\\|", 2); String t = x.length > 1 ? x[1] : ""; yield sender + "이 " + x[0] + "에 모임 「" + t + "」" + objectJosa(t) + " 열었습니다."; }
            case "EVENT_TOMORROW" -> "내일 모임 「" + nz(extra) + "」" + subjectJosa(nz(extra)) + " 있어요.";
            case "EVENT_SOON"     -> "모임 「" + nz(extra) + "」" + subjectJosa(nz(extra)) + " 곧 시작해요.";
            case "EVENT_CANCEL"   -> "참석하려던 모임 「" + nz(extra) + "」" + subjectJosa(nz(extra)) + " 취소됐어요.";
            case "BADGE"       -> "새 뱃지 " + (extra == null ? "" : extra) + objectJosa(extra) + " 얻었어요!";
            case "FOLLOW"      -> sender + "이 팔로우를 시작했습니다.";
            case "COMM_POST"   -> sender + "이 " + place + "에 새 글을 올렸습니다.";
            case "COMM_NOTICE" -> place + "에 새 공지가 올라왔습니다.";
            case "COMM_APPROVED"  -> place + " 가입이 승인됐습니다. 환영해요!";
            case "COMM_REJECTED"  -> place + " 가입 신청이 받아들여지지 않았습니다.";
            case "COMM_REMOVED"   -> place + "에서 내보내졌습니다.";
            case "COMM_ADMIN"     -> place + "의 운영진이 됐습니다.";
            case "COMM_ADMIN_OFF" -> place + "의 운영진에서 해제됐습니다.";
            case "COMM_OWNER"     -> sender + "이 " + place + "의 주인 자리를 넘겼습니다.";
            case "KEYWORD"        -> { String[] x = nz(extra).split("\\|", 2); yield "관심 키워드 「" + x[0] + "」 새 글: " + (x.length > 1 ? x[1] : ""); }
            case "SERIES_NEW"     -> { String[] x = nz(extra).split("\\|", 2); yield "구독 중인 연재 「" + x[0] + "」 새 편: " + (x.length > 1 ? x[1] : ""); }
            case "NEW_LOGIN"      -> "새 기기에서 로그인했어요: " + nz(extra) + ". 본인이 아니면 비밀번호를 바꾸고 그 기기를 로그아웃하세요.";
            case "REPORT_HIDDEN"  -> "신고가 여러 건 들어와 회원님의 " + nz(extra) + subjectJosa(nz(extra)) + " 검토 중이에요. 문제가 없으면 다시 보여요.";
            default            -> "새 알림이 있습니다. (" + notifTyp + ")";
        };
        // MESSAGE 컬럼은 255자다 — 커뮤니티 이름이 길어도 넘치지 않게 자른다.
        return msg.length() > 250 ? msg.substring(0, 250) + "…" : msg;
    }

    /** 을/를 — 마지막 한글 글자에 받침이 있으면 「을」. 「첫 글」을 · 「작은 박수」를 · 외 3개를 */
    static String objectJosa(String word) {
        if (word == null) return "를";
        for (int k = word.length() - 1; k >= 0; k--) {
            char ch = word.charAt(k);
            if (ch >= 0xAC00 && ch <= 0xD7A3) return ((ch - 0xAC00) % 28) == 0 ? "를" : "을";
            if (Character.isDigit(ch)) return "013678".indexOf(ch) >= 0 ? "을" : "를";
        }
        return "를";
    }

    /** 이/가 — 받침이 있으면 「이」 */
    static String subjectJosa(String word) { return "을".equals(objectJosa(word)) ? "이" : "가"; }

    private static String nz(String s) { return s == null ? "" : s; }
}
