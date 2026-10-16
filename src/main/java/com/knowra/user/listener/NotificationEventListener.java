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
        // 받는 사람의 언어·시간대 — 알림 문장과 푸시를 그 언어로
        com.knowra.user.entity.TblUserStng rst = stngRepository.findByUserSn(event.receiverSn());
        String lang = rst == null || rst.getLang() == null ? com.knowra.cmm.i18n.Lang.KO : rst.getLang();
        String tz = rst == null ? null : rst.getTimeZone();
        if (anonymous) { nickName = tr(lang, com.knowra.post.service.PostQuery.ANONYMOUS_NAME); pfpUrl = null; }

        String message = buildMessage(lang, tz, event.notifTyp(), nickName, event.extra());

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
            case "COMMENT", "REPLY", "MENTION", "CMT_PINNED" -> st.getCmtNtfctnYn();
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
        if ("MODMAIL".equals(e.targetKind()) && e.targetSn() != null) return "/modmail/" + e.targetSn();
        if ("FOLLOW".equals(e.notifTyp()) && senderNick != null) return "/user/" + senderNick;
        if ("COMM".equals(e.targetKind()) && e.targetSn() != null) {
            com.knowra.community.entity.TblComm c = commRepository.findByCommSn(e.targetSn());
            if (c != null) return "/community/" + c.getCommNm();
        }
        return "/";
    }

    /**
     * 알림 문장 — <b>받는 사람의 언어로</b>. 원문은 한국어 템플릿이고(번역 표의 키), 조사는 「이(가)」처럼 적어 두면
     * 한국어일 때만 앞말을 보고 고른다(Josa). 날짜는 받는 사람의 시간대로.
     */
    String buildMessage(String lang, String tz, String notifTyp, String nickName, String extra) {
        boolean ko = com.knowra.cmm.i18n.Lang.KO.equals(lang);
        String sender = nickName != null ? (ko ? nickName + "님" : nickName) : tr(lang, "누군가");
        String place  = extra != null ? extra : tr(lang, "커뮤니티");
        String[] x = nz(extra).split("\\|", 3);
        String a0 = x[0], a1 = x.length > 1 ? x[1] : "", a2 = x.length > 2 ? x[2] : "";
        String msg = switch (notifTyp) {
            case "LIKE"        -> tr(lang, "{0}이(가) 내 게시글을 좋아합니다.", sender);
            case "COMMENT"     -> tr(lang, "{0}이(가) 내 게시글에 댓글을 달았습니다.", sender);
            case "REPLY"       -> tr(lang, "{0}이(가) 내 댓글에 답글을 달았습니다.", sender);
            case "MENTION"     -> tr(lang, "{0}이(가) 회원님을 언급했습니다.", sender);
            case "EVENT_NEW"      -> tr(lang, "{0}이(가) {1}에 모임 「{2}」을(를) 열었습니다.", sender, a0, a1);
            // 하루 전(20~24시간 남음) — 「내일」이라 쓰면 오늘 밤 모임에 틀린 말이 된다
            case "EVENT_TOMORROW" -> tr(lang, "모임 「{0}」이(가) 하루 앞으로 다가왔어요.", nz(extra));
            case "EVENT_SOON"     -> tr(lang, "모임 「{0}」이(가) 곧 시작해요.", nz(extra));
            case "EVENT_CANCEL"   -> tr(lang, "참석하려던 모임 「{0}」이(가) 취소됐어요.", nz(extra));
            case "BADGE"       -> tr(lang, "새 뱃지 {0}을(를) 얻었어요!", nz(extra));
            case "FOLLOW"      -> tr(lang, "{0}이(가) 팔로우를 시작했습니다.", sender);
            case "COMM_POST"   -> tr(lang, "{0}이(가) {1}에 새 글을 올렸습니다.", sender, place);
            case "COMM_NOTICE" -> tr(lang, "{0}에 새 공지가 올라왔습니다.", place);
            case "COMM_APPROVED"  -> tr(lang, "{0} 가입이 승인됐습니다. 환영해요!", place);
            case "COMM_REJECTED"  -> tr(lang, "{0} 가입 신청이 받아들여지지 않았습니다.", place);
            case "COMM_REMOVED"   -> tr(lang, "{0}에서 내보내졌습니다.", place);
            // 「장소|풀리는 때(ISO, 비면 영구)|사유」 — 날짜는 받는 사람의 언어·시간대로
            case "COMM_BANNED"    -> (a1.isEmpty() ? tr(lang, "{0}에서 내보내졌어요. 다시 참여할 수 없어요.", a0)
                                                   : tr(lang, "{0}에서 내보내졌어요. {1}까지 다시 참여할 수 없어요.", a0, untilText(a1, lang, tz)))
                                     + (a2.isEmpty() ? "" : " " + tr(lang, "사유: {0}", a2));
            case "COMM_UNBANNED"  -> tr(lang, "{0} 강퇴가 풀렸어요. 원하면 다시 참여할 수 있어요.", place);
            case "COMM_ADMIN"     -> tr(lang, "{0}의 운영진이 됐습니다.", place);
            case "COMM_ADMIN_OFF" -> tr(lang, "{0}의 운영진에서 해제됐습니다.", place);
            case "COMM_OWNER"     -> tr(lang, "{0}이(가) {1}의 주인 자리를 넘겼습니다.", sender, place);
            case "KEYWORD"        -> tr(lang, "관심 키워드 「{0}」 새 글: {1}", a0, a1);
            case "SERIES_NEW"     -> tr(lang, "구독 중인 연재 「{0}」 새 편: {1}", a0, a1);
            case "CMT_PINNED"     -> tr(lang, "{0}이(가) 「{1}」에서 회원님의 댓글을 맨 위에 고정했어요.", sender, nz(extra));
            case "HOLD_NEW"       -> tr(lang, "{0}에 검토할 새 멤버 글이 있어요.", place);
            case "MODMAIL_NEW"    -> tr(lang, "{0} 운영진 문의함에 새 문의가 왔어요.", place);
            case "MODMAIL_REPLY"  -> tr(lang, "{0} 운영진이 문의에 답했어요.", place);
            case "HOLD_APPROVED"  -> tr(lang, "{0} 운영진이 확인해 글이 올라갔어요: {1}", a0, a1);
            case "HOLD_REJECTED"  -> tr(lang, "{0} 운영진이 확인한 뒤 글을 올리지 않았어요: {1}", a0, a1);
            case "NEW_LOGIN"      -> tr(lang, "새 기기에서 로그인했어요: {0}. 본인이 아니면 비밀번호를 바꾸고 그 기기를 로그아웃하세요.", nz(extra));
            // extra 는 「글」·「댓글」 — 그 말도 받는 사람의 언어로
            case "REPORT_HIDDEN"  -> tr(lang, "신고가 여러 건 들어와 회원님의 {0}이(가) 검토 중이에요. 문제가 없으면 다시 보여요.", tr(lang, nz(extra)));
            default            -> tr(lang, "새 알림이 있습니다. ({0})", notifTyp);
        };
        // MESSAGE 컬럼은 255자다 — 커뮤니티 이름이 길어도 넘치지 않게 자른다.
        return msg.length() > 250 ? msg.substring(0, 250) + "…" : msg;
    }

    /** 번역하고, 한국어면 조사를 고른다 */
    private static String tr(String lang, String ko, Object... args) {
        String s = com.knowra.cmm.i18n.Msg.tl(lang, ko, args);
        return com.knowra.cmm.i18n.Lang.KO.equals(lang) ? com.knowra.cmm.i18n.Josa.resolve(s) : s;
    }

    private static String untilText(String iso, String lang, String tz) {
        try { return com.knowra.cmm.i18n.Fmt.dateTime(java.time.LocalDateTime.parse(iso), lang, tz); }
        catch (Exception e) { return iso; }   // 예전 형식(「10월 18일 23:13」)이 남아 있으면 그대로
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
