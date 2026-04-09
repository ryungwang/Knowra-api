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

    @PersistenceContext
    private EntityManager em;

    @Async
    @EventListener
    @Transactional
    public void onNotifTrigger(NotifTriggerEvent event) {
        // 자기 자신에게는 알림 미발송
        if (event.senderSn() != null && event.receiverSn() == event.senderSn()) return;

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

        String message = buildMessage(event.notifTyp(), nickName);

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
    }

    private String buildMessage(String notifTyp, String nickName) {
        String sender = nickName != null ? nickName + "님" : "누군가";
        return switch (notifTyp) {
            case "LIKE"    -> sender + "이 내 게시글을 좋아합니다.";
            case "COMMENT" -> sender + "이 내 게시글에 댓글을 달았습니다.";
            case "FOLLOW"  -> sender + "이 팔로우를 시작했습니다.";
            default        -> "새 알림이 있습니다. (" + notifTyp + ")";
        };
    }
}
