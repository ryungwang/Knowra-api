package com.knowra.chat.service;

import com.knowra.chat.entity.TblChatMsg;
import com.knowra.chat.entity.TblChatRoom;
import com.knowra.chat.entity.TblChatRoomMbr;
import com.knowra.chat.repository.TblChatMsgRepository;
import com.knowra.chat.repository.TblChatRoomMbrRepository;
import com.knowra.chat.repository.TblChatRoomRepository;
import com.knowra.cmm.handler.NotificationWebSocketHandler;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.common.entity.QTblComFile;
import com.knowra.user.entity.QTblUser;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 1:1 채팅.
 *
 * <p>메시지는 REST 로 보내고(저장이 먼저다), 상대가 접속 중이면 알림 소켓으로
 * {@code type:"CHAT"} 이벤트를 밀어 준다. 소켓이 끊겨 있어도 다음에 방을 열면 DB 에서 읽는다 —
 * 실시간은 덤이고 저장이 본체다.
 *
 * <p>읽음은 방 멤버의 {@code lastReadMsgSn} 하나로 센다. 안 읽은 수 = 그 번호보다 큰 메시지 수.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_LEN = 2000;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final TblChatRoomRepository roomRepository;
    private final TblChatRoomMbrRepository roomMbrRepository;
    private final TblChatMsgRepository msgRepository;
    private final TblUserRepository userRepository;
    private final NotificationWebSocketHandler wsHandler;

    @PersistenceContext
    private EntityManager em;

    // ── 방 목록 ───────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ResultVO getRoomList(Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            List<TblChatRoomMbr> mine = roomMbrRepository.findAllByUserSn(userSn);
            if (mine.isEmpty()) {
                resultVO.putResult("list", List.of());
                resultVO.putResult("unreadTotal", 0);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                return resultVO;
            }
            List<Long> roomSns = mine.stream().map(TblChatRoomMbr::getRoomSn).toList();
            Map<Long, TblChatRoom> rooms = new HashMap<>();
            roomRepository.findAllById(roomSns).forEach(r -> rooms.put(r.getRoomSn(), r));

            // 상대 멤버 (1:1 이라 방마다 한 명)
            QTblUser    u   = QTblUser.tblUser;
            QTblComFile pfp = new QTblComFile("pfp");
            com.knowra.chat.entity.QTblChatRoomMbr m = com.knowra.chat.entity.QTblChatRoomMbr.tblChatRoomMbr;
            List<Tuple> peers = new JPAQueryFactory(em)
                    .select(m.roomSn, u.userSn, u.nickName, u.name, pfp.atchFilePathNm, pfp.strgFileNm, pfp.atchFileExtnNm)
                    .from(m).join(u).on(m.userSn.eq(u.userSn)).leftJoin(u.pfp, pfp)
                    .where(m.roomSn.in(roomSns).and(m.userSn.ne(userSn)))
                    .fetch();
            Map<Long, Map<String, Object>> peerByRoom = new HashMap<>();
            for (Tuple t : peers) {
                String path = t.get(pfp.atchFilePathNm);
                Map<String, Object> p = new HashMap<>();
                p.put("userSn",   t.get(u.userSn));
                p.put("nickName", t.get(u.nickName));
                p.put("name",     t.get(u.name));
                p.put("pfpUrl",   path != null ? path + "/" + t.get(pfp.strgFileNm) + "." + t.get(pfp.atchFileExtnNm) : null);
                peerByRoom.put(t.get(m.roomSn), p);
            }

            // 마지막 메시지 본문 + 안 읽은 수
            com.knowra.chat.entity.QTblChatMsg msg = com.knowra.chat.entity.QTblChatMsg.tblChatMsg;
            List<Long> lastSns = rooms.values().stream().map(TblChatRoom::getLastMsgSn).filter(Objects::nonNull).toList();
            Map<Long, TblChatMsg> lastMsgs = new HashMap<>();
            if (!lastSns.isEmpty()) msgRepository.findAllById(lastSns).forEach(x -> lastMsgs.put(x.getMsgSn(), x));

            List<Map<String, Object>> list = new ArrayList<>();
            long unreadTotal = 0;
            for (TblChatRoomMbr mb : mine) {
                TblChatRoom room = rooms.get(mb.getRoomSn());
                if (room == null) continue;
                long unread = new JPAQueryFactory(em).select(msg.count()).from(msg)
                        .where(msg.roomSn.eq(room.getRoomSn())
                                .and(msg.msgSn.gt(mb.getLastReadMsgSn()))
                                .and(msg.userSn.ne(userSn)))
                        .fetchOne();
                unreadTotal += unread;
                TblChatMsg last = room.getLastMsgSn() != null ? lastMsgs.get(room.getLastMsgSn()) : null;
                Map<String, Object> row = new HashMap<>();
                row.put("roomSn",    room.getRoomSn());
                row.put("peer",      peerByRoom.get(room.getRoomSn()));
                row.put("lastMsg",   last != null ? last.getMsgCntnt() : null);
                row.put("lastMsgAt", room.getLastMsgAt() != null ? room.getLastMsgAt().format(ISO) : null);
                row.put("unread",    unread);
                list.add(row);
            }
            // 최근 대화 순. 메시지가 없는 방(막 만든 방)은 맨 위.
            list.sort((a, b) -> {
                String x = (String) a.get("lastMsgAt"), y = (String) b.get("lastMsgAt");
                if (x == null) return -1; if (y == null) return 1;
                return y.compareTo(x);
            });
            resultVO.putResult("list", list);
            resultVO.putResult("unreadTotal", unreadTotal);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ── DM 방 열기(없으면 생성) ────────────────────────────────────────────
    @Transactional
    public ResultVO openDm(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            String nickName = String.valueOf(params.get("nickName"));
            TblUser peer = userRepository.findByNickName(nickName)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            if (peer.getUserSn() == userSn) throw new IllegalArgumentException("자기 자신과는 대화할 수 없습니다.");

            String key = TblChatRoom.dmKeyOf(userSn, peer.getUserSn());
            TblChatRoom room = roomRepository.findByDmKey(key).orElseGet(() -> {
                TblChatRoom r = roomRepository.save(TblChatRoom.builder().dmKey(key).build());
                roomMbrRepository.save(TblChatRoomMbr.builder().roomSn(r.getRoomSn()).userSn(userSn).build());
                roomMbrRepository.save(TblChatRoomMbr.builder().roomSn(r.getRoomSn()).userSn(peer.getUserSn()).build());
                return r;
            });
            resultVO.putResult("roomSn", room.getRoomSn());
            resultVO.putResult("peer", Map.of("userSn", peer.getUserSn(), "nickName", peer.getNickName(), "name", peer.getName()));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ── 메시지 목록 (오래된 쪽으로 커서) ──────────────────────────────────
    @Transactional(readOnly = true)
    public ResultVO getMsgList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            long roomSn = Long.parseLong(params.get("roomSn").toString());
            int  size   = params.get("size")   != null ? Integer.parseInt(params.get("size").toString()) : 30;
            Long cursor = params.get("cursor") != null ? Long.parseLong(params.get("cursor").toString()) : null;
            requireMember(roomSn, userSn);

            com.knowra.chat.entity.QTblChatMsg msg = com.knowra.chat.entity.QTblChatMsg.tblChatMsg;
            var cond = msg.roomSn.eq(roomSn);
            if (cursor != null) cond = cond.and(msg.msgSn.lt(cursor));
            List<TblChatMsg> rows = new JPAQueryFactory(em).selectFrom(msg)
                    .where(cond).orderBy(msg.msgSn.desc()).limit(size + 1).fetch();
            boolean more = rows.size() > size;
            if (more) rows = rows.subList(0, size);
            Collections.reverse(rows);   // 화면은 위가 과거

            List<Map<String, Object>> list = rows.stream().map(this::toDto).toList();
            resultVO.putResult("list", list);
            resultVO.putResult("nextCursor", more && !rows.isEmpty() ? rows.get(0).getMsgSn() : null);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ── 보내기 ────────────────────────────────────────────────────────────
    @Transactional
    public ResultVO sendMsg(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            long roomSn = Long.parseLong(params.get("roomSn").toString());
            String text = params.get("msgCntnt") == null ? "" : params.get("msgCntnt").toString().trim();
            if (text.isEmpty()) throw new IllegalArgumentException("메시지가 비어 있습니다.");
            if (text.length() > MAX_LEN) text = text.substring(0, MAX_LEN);
            requireMember(roomSn, userSn);

            TblChatMsg saved = msgRepository.save(TblChatMsg.builder().roomSn(roomSn).userSn(userSn).msgCntnt(text).build());
            TblChatRoom room = roomRepository.findById(roomSn).orElseThrow();
            room.setLastMsgSn(saved.getMsgSn());
            room.setLastMsgAt(saved.getFrstCrtDt());
            roomRepository.save(room);
            // 내가 보낸 건 내가 읽은 것이다
            roomMbrRepository.findByRoomSnAndUserSn(roomSn, userSn).ifPresent(mb -> {
                mb.setLastReadMsgSn(saved.getMsgSn()); roomMbrRepository.save(mb);
            });

            Map<String, Object> dto = toDto(saved);
            for (TblChatRoomMbr mb : roomMbrRepository.findAllByRoomSn(roomSn)) {
                if (mb.getUserSn() != userSn) wsHandler.pushEvent(mb.getUserSn(), "CHAT", dto);
            }
            resultVO.putResult("msg", dto);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    // ── 읽음 ──────────────────────────────────────────────────────────────
    @Transactional
    public ResultVO markRead(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            long roomSn = Long.parseLong(params.get("roomSn").toString());
            TblChatRoomMbr mb = requireMember(roomSn, userSn);
            TblChatRoom room = roomRepository.findById(roomSn).orElseThrow();
            if (room.getLastMsgSn() != null && room.getLastMsgSn() > mb.getLastReadMsgSn()) {
                mb.setLastReadMsgSn(room.getLastMsgSn());
                roomMbrRepository.save(mb);
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    private TblChatRoomMbr requireMember(long roomSn, long userSn) {
        return roomMbrRepository.findByRoomSnAndUserSn(roomSn, userSn)
                .orElseThrow(() -> new IllegalArgumentException("참여하지 않은 대화방입니다."));
    }

    private Map<String, Object> toDto(TblChatMsg m) {
        Map<String, Object> d = new HashMap<>();
        d.put("msgSn",     m.getMsgSn());
        d.put("roomSn",    m.getRoomSn());
        d.put("userSn",    m.getUserSn());
        d.put("msgCntnt",  m.getMsgCntnt());
        d.put("createdAt", m.getFrstCrtDt().format(ISO));
        return d;
    }
}
