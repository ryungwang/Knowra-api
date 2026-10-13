package com.knowra.user.controller;

import com.knowra.cmm.handler.NotificationWebSocketHandler;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.user.dto.NotifDTO;
import com.knowra.user.entity.QTblUserNotif;
import com.knowra.user.entity.TblUserNotif;
import com.knowra.user.service.NotifService;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 알림 페이지 — 전체 기록을 쪽 단위로, 종류별로. 실시간 수신·읽음·지우기는 여전히 웹소켓이 맡는다.
 * group: all | talk(댓글·답글·멘션) | like | follow | comm(커뮤니티 소식)
 */
@RestController
@RequestMapping("/api/notif")
@RequiredArgsConstructor
public class NotifController {

    private static final int SIZE = 20;
    private final NotificationWebSocketHandler ws;
    private final NotifService notifService;

    @PersistenceContext
    private EntityManager em;

    /** body: group, cursor(notifSn — 이보다 오래된 것) */
    @PostMapping("/list")
    public ResultVO list(@RequestBody(required = false) Map<String, Object> b, @CurrentUser Long me) {
        String group = b == null || b.get("group") == null ? "all" : String.valueOf(b.get("group"));
        Long cursor = b == null || b.get("cursor") == null ? null : Long.valueOf(String.valueOf(b.get("cursor")));
        QTblUserNotif n = QTblUserNotif.tblUserNotif;
        BooleanExpression w = n.userSn.eq(me).and(n.isDisplay.eq("Y"));
        w = switch (group) {
            case "talk" -> w.and(n.notifTyp.in("COMMENT", "REPLY", "MENTION"));
            case "like" -> w.and(n.notifTyp.eq("LIKE"));
            case "follow" -> w.and(n.notifTyp.eq("FOLLOW"));
            case "comm" -> w.and(n.notifTyp.startsWith("COMM_"));
            default -> w;
        };
        if (cursor != null) w = w.and(n.notifSn.lt(cursor));
        List<TblUserNotif> rows = new JPAQueryFactory(em).selectFrom(n).where(w).orderBy(n.notifSn.desc()).limit(SIZE + 1).fetch();
        boolean more = rows.size() > SIZE;
        if (more) rows = rows.subList(0, SIZE);
        Map<Long, String[]> info = ws.fetchSenderInfoMap(rows);
        Map<Long, String> comm = ws.fetchCommNames(rows);
        List<NotifDTO> list = rows.stream().map(e -> {
            String[] i = e.getSenderSn() == null ? null : info.get(e.getSenderSn());
            if (i == null) i = new String[]{null, null};   // 보낸 사람 없는 알림(뱃지 등) — getOrDefault(null) 은 불변 Map 에서 NPE
            return new NotifDTO(e, i[0], i[1], "COMM".equals(e.getTargetKind()) && e.getTargetSn() != null ? comm.get(e.getTargetSn()) : null);
        }).toList();
        ResultVO r = new ResultVO();
        r.putResult("list", list);
        r.putResult("nextCursor", more ? rows.get(rows.size() - 1).getNotifSn() : null);
        r.putResult("unreadCnt", notifService.countUnread(me));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }
}
