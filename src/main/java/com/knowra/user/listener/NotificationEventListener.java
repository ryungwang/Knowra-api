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

    @Async
    @EventListener
    @Transactional
    public void onNotifTrigger(NotifTriggerEvent event) {
        // 자기 자신에게는 알림 미발송
        if (event.senderSn() != null && event.receiverSn() == event.senderSn()) return;
        // 차단 관계면 양쪽 다 조용히 — 차단한 사람의 반응이 알림으로 새면 차단의 의미가 없다
        if (event.senderSn() != null && safetyService.blockedEitherWay(event.receiverSn(), event.senderSn())) return;

        // 발신자 닉네임·프로필 조회
        String nickName = null;
        String pfpUrl   = null;
        if (event.senderSn() != null) {
            QTblUser    qUser   = QTblUser.tblUser;
            QTblComFile pfpFile = new QTblComFile("pfpFile");
            Tuple t = new JPAQueryFactory(em)
                    .select(qUser.nickName,
                            pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                    .from(qUser)
                    .leftJoin(qUser.pfp, pfpFile)
                    .where(qUser.userSn.eq(event.senderSn()))
                    .fetchOne();
            if (t != null) {
                nickName      = t.get(qUser.nickName);
                String pathNm = t.get(pfpFile.atchFilePathNm);
                pfpUrl        = pathNm != null
                        ? pathNm + "/" + t.get(pfpFile.strgFileNm) + "." + t.get(pfpFile.atchFileExtnNm)
                        : null;
            }
        }

        String message = buildMessage(event.notifTyp(), nickName, event.extra());

        TblUserNotif entity = TblUserNotif.builder()
                .userSn(event.receiverSn())
                .senderSn(event.senderSn())
                .notifTyp(event.notifTyp())
                .message(message)
                .targetSn(event.targetSn())
                .targetKind(event.targetKind())
                .build();
        notifRepository.save(entity);

        wsHandler.push(event.receiverSn(), new NotifDTO(entity, nickName, pfpUrl));

        // 웹 푸시 — 설정의 알림 종류 스위치를 따른다. 실패해도 인앱 알림은 이미 저장됐다.
        if (wantsPush(event.receiverSn(), event.notifTyp())) {
            try {
                java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("title", "knowra");
                payload.put("body", message);
                payload.put("url", linkFor(event, nickName));
                payload.put("tag", event.notifTyp() + ":" + event.targetSn());   // 같은 글의 좋아요가 쌓이지 않고 바뀐다
                webPushService.sendToUser(event.receiverSn(), payload);
            } catch (Exception ignore) { /* 푸시는 덤이다 */ }
        }
    }

    /** 알림 설정: 좋아요=like, 댓글·답글=cmt, 팔로우=flwr, 나머지(커뮤니티 소식)=sys. 설정 행이 없으면 기본값. */
    private boolean wantsPush(long userSn, String typ) {
        com.knowra.user.entity.TblUserStng st = stngRepository.findByUserSn(userSn);
        if (st == null) return !"LIKE".equals(typ);
        String yn = switch (typ) {
            case "LIKE" -> st.getLikeNtfctnYn();
            case "COMMENT", "REPLY", "MENTION" -> st.getCmtNtfctnYn();
            case "FOLLOW" -> st.getFlwrNtfctnYn();
            default -> st.getSysNtfctnYn();
        };
        return "Y".equals(yn);
    }

    /** 알림을 눌렀을 때 갈 곳 — 헤더 알림 목록과 같은 규칙. */
    private String linkFor(NotifTriggerEvent e, String senderNick) {
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
            case "FOLLOW"      -> sender + "이 팔로우를 시작했습니다.";
            case "COMM_POST"   -> sender + "이 " + place + "에 새 글을 올렸습니다.";
            case "COMM_NOTICE" -> place + "에 새 공지가 올라왔습니다.";
            case "COMM_APPROVED"  -> place + " 가입이 승인됐습니다. 환영해요!";
            case "COMM_REJECTED"  -> place + " 가입 신청이 받아들여지지 않았습니다.";
            case "COMM_REMOVED"   -> place + "에서 내보내졌습니다.";
            case "COMM_ADMIN"     -> place + "의 운영진이 됐습니다.";
            case "COMM_ADMIN_OFF" -> place + "의 운영진에서 해제됐습니다.";
            default            -> "새 알림이 있습니다. (" + notifTyp + ")";
        };
        // MESSAGE 컬럼은 255자다 — 커뮤니티 이름이 길어도 넘치지 않게 자른다.
        return msg.length() > 250 ? msg.substring(0, 250) + "…" : msg;
    }
}
