package com.knowra.chat.service;

import com.knowra.cmm.errlog.ErrorLog;
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
 * 채팅 — 1:1 DM 과 그룹(ChatGroupService).
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
    private final com.knowra.user.repository.TblUserFlwrRepository flwrRepository;
    private final TblChatMsgRepository msgRepository;
    private final TblUserRepository userRepository;
    private final NotificationWebSocketHandler wsHandler;
    private final com.knowra.safety.service.SafetyService safetyService;
    private final ChatGroupService groupService;

    @PersistenceContext
    private EntityManager em;

    // ── 방 목록 ───────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ResultVO getRoomList(Long me) { return getRoomList(me, "main"); }

    /** box: main(내 대화) | request(메시지 요청). 안 읽은 수(unreadTotal)는 main 만 센다. */
    public ResultVO getRoomList(Long me, String box) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            List<TblChatRoomMbr> all = roomMbrRepository.findAllByUserSn(userSn);
            String want = "request".equals(box) ? "REQUEST" : "ACTIVE";
            List<TblChatRoomMbr> mine = all.stream().filter(x -> want.equals(x.getStat())).toList();
            resultVO.putResult("requestCount", all.stream().filter(x -> "REQUEST".equals(x.getStat())).count());
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
                    .select(m.roomSn, u.userSn, u.nickName, u.name, pfp.atchFilePathNm, pfp.strgFileNm, pfp.atchFileExtnNm, pfp.fileKey)
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
                p.put("pfpUrl",   com.knowra.cmm.storage.StorageUrls.of(path, t.get(pfp.strgFileNm), t.get(pfp.atchFileExtnNm), t.get(pfp.fileKey)));
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
                row.put("roomTyp",   room.getRoomTyp());
                if (ChatGroupService.GROUP.equals(room.getRoomTyp())) {
                    // 그룹은 상대 한 명이 아니라 방 이름·인원
                    long cnt = roomMbrRepository.findAllByRoomSn(room.getRoomSn()).stream().filter(x -> "ACTIVE".equals(x.getStat())).count();
                    row.put("group", Map.of("name", room.getName() == null ? "그룹" : room.getName(), "memberCnt", cnt));
                } else {
                    row.put("peer",  peerByRoom.get(room.getRoomSn()));
                }
                row.put("lastMsg",   last == null ? null : last.getDeletedAt() != null ? "삭제된 메시지" : last.getHiddenAt() != null ? "가려진 메시지" : (last.getMsgCntnt().isEmpty() && last.getImgUrl() != null ? "사진" : last.getMsgCntnt()));
                row.put("stat",      mb.getStat());
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
            ErrorLog.report(e);
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
            if (safetyService.blockedEitherWay(userSn, peer.getUserSn())) throw new IllegalArgumentException("대화할 수 없는 상대예요.");

            String key = TblChatRoom.dmKeyOf(userSn, peer.getUserSn());
            // 상대가 나를 팔로우하고 있으면 바로 대화, 아니면 상대 쪽은 「메시지 요청」으로 — 모르는 사람의 DM 이
            // 대화 목록과 안 읽은 수를 채우지 않게.
            boolean peerFollowsMe = flwrRepository.findByFlwrUserSnAndFlwngUserSn(peer.getUserSn(), userSn)
                    .filter(f -> "Y".equals(f.getActvtnYn())).isPresent();
            TblChatRoom room = roomRepository.findByDmKey(key).orElseGet(() -> {
                TblChatRoom r = roomRepository.save(TblChatRoom.builder().dmKey(key).build());
                roomMbrRepository.save(TblChatRoomMbr.builder().roomSn(r.getRoomSn()).userSn(userSn).build());
                roomMbrRepository.save(TblChatRoomMbr.builder().roomSn(r.getRoomSn()).userSn(peer.getUserSn())
                        .stat(peerFollowsMe ? "ACTIVE" : "REQUEST").build());
                return r;
            });
            // 상대가 먼저 걸어 온 요청을 내가 직접 열었다 = 받아들인 것
            roomMbrRepository.findByRoomSnAndUserSn(room.getRoomSn(), userSn).ifPresent(mb -> {
                if (!"ACTIVE".equals(mb.getStat())) { mb.setStat("ACTIVE"); roomMbrRepository.save(mb); }
            });
            resultVO.putResult("roomSn", room.getRoomSn());
            resultVO.putResult("peer", Map.of("userSn", peer.getUserSn(), "nickName", peer.getNickName(), "name", peer.getName()));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
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

            TblChatRoom room = roomRepository.findById(roomSn).orElseThrow();
            boolean group = ChatGroupService.GROUP.equals(room.getRoomTyp());
            // 그룹에선 차단 관계인 사람의 메시지를 서로 안 보여 준다(방은 같이 쓴다) — DM 은 애초에 보낼 수 없다
            Set<Long> blocked = new HashSet<>();
            if (group) for (TblChatRoomMbr x : roomMbrRepository.findAllByRoomSn(roomSn))
                if (x.getUserSn() != userSn && safetyService.blockedEitherWay(userSn, x.getUserSn())) blocked.add(x.getUserSn());
            List<Map<String, Object>> list = rows.stream().filter(x -> !blocked.contains(x.getUserSn())).map(this::toDto).toList();
            resultVO.putResult("list", list);
            Map<String, Object> roomInfo = new HashMap<>();
            roomInfo.put("roomTyp", room.getRoomTyp());
            if (group) {
                roomInfo.put("name", room.getName());
                roomInfo.put("createdBy", room.getCreatedBy());
                roomInfo.put("eventSn", room.getEventSn());
                resultVO.putResult("members", groupService.members(roomSn));
            }
            resultVO.putResult("room", roomInfo);
            resultVO.putResult("nextCursor", more && !rows.isEmpty() ? rows.get(0).getMsgSn() : null);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {   // 나간 방·남의 방 — 오류가 아니라 안내
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
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
            String imgUrl = com.knowra.post.service.PostService.cleanImgUrl(params.get("imgUrl"));
            if (text.isEmpty() && imgUrl == null) throw new IllegalArgumentException("메시지가 비어 있습니다.");
            if (text.length() > MAX_LEN) text = text.substring(0, MAX_LEN);
            TblChatRoomMbr myMb = requireMember(roomSn, userSn);
            if (!"ACTIVE".equals(myMb.getStat())) { myMb.setStat("ACTIVE"); roomMbrRepository.save(myMb); }   // 답장 = 수락
            boolean group = ChatGroupService.GROUP.equals(roomRepository.findById(roomSn).map(TblChatRoom::getRoomTyp).orElse("DM"));
            // DM 은 차단 관계면 못 보낸다. 그룹은 보내되 차단 관계인 사람에겐 가지 않는다(아래)
            if (!group) for (TblChatRoomMbr other : roomMbrRepository.findAllByRoomSn(roomSn)) {
                if (other.getUserSn() != userSn && safetyService.blockedEitherWay(userSn, other.getUserSn()))
                    throw new IllegalArgumentException("대화할 수 없는 상대예요.");
            }

            TblChatMsg saved = msgRepository.save(TblChatMsg.builder().roomSn(roomSn).userSn(userSn).msgCntnt(text).imgUrl(imgUrl).build());
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
                if (mb.getUserSn() == userSn || "DECLINED".equals(mb.getStat()) || "LEFT".equals(mb.getStat())) continue;   // 거절·나간 사람에겐 아무것도 안 간다
                if (group && safetyService.blockedEitherWay(userSn, mb.getUserSn())) continue;
                Map<String, Object> ev = new HashMap<>(dto);
                ev.put("request", "REQUEST".equals(mb.getStat()));   // 요청함 쪽은 배지만, 대화 목록·안 읽은 수엔 안 섞는다
                wsHandler.pushEvent(mb.getUserSn(), "CHAT", ev);
            }
            resultVO.putResult("msg", dto);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    // ── 보낸 메시지 지우기 ────────────────────────────────────────────────
    /** params: msgSn — 보낸 사람만. 같은 방 사람들에게 바뀐 메시지를 밀어 준다(열린 화면이 바로 바뀐다) */
    @Transactional
    public ResultVO deleteMsg(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long msgSn = Long.parseLong(String.valueOf(params.get("msgSn")));
            TblChatMsg m = msgRepository.findById(msgSn).orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없어요."));
            if (m.getUserSn() != me) throw new IllegalArgumentException("내가 보낸 메시지만 지울 수 있어요.");
            requireMember(m.getRoomSn(), me);
            if (m.getDeletedAt() == null) {
                m.setDeletedAt(LocalDateTime.now());
                msgRepository.save(m);
                Map<String, Object> dto = toDto(m);
                for (TblChatRoomMbr mb : roomMbrRepository.findAllByRoomSn(m.getRoomSn())) {
                    if ("DECLINED".equals(mb.getStat()) || "LEFT".equals(mb.getStat())) continue;
                    Map<String, Object> ev = new HashMap<>(dto);
                    ev.put("request", "REQUEST".equals(mb.getStat()));
                    wsHandler.pushEvent(mb.getUserSn(), "CHAT", ev);
                }
            }
            resultVO.putResult("msg", toDto(m));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
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
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 메시지 요청 수락·거절. params: roomSn, accept(true|false) */
    @Transactional
    public ResultVO respondRequest(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long roomSn = Long.parseLong(String.valueOf(params.get("roomSn")));
            boolean accept = Boolean.parseBoolean(String.valueOf(params.get("accept")));
            TblChatRoomMbr mb = requireMember(roomSn, me);
            mb.setStat(accept ? "ACTIVE" : "DECLINED");
            roomMbrRepository.save(mb);
            resultVO.putResult("stat", mb.getStat());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        }
        return resultVO;
    }

    private TblChatRoomMbr requireMember(long roomSn, long userSn) {
        return roomMbrRepository.findByRoomSnAndUserSn(roomSn, userSn).filter(m -> !"LEFT".equals(m.getStat()))
                .orElseThrow(() -> new IllegalArgumentException("참여하지 않은 대화방입니다."));
    }

    private Map<String, Object> toDto(TblChatMsg m) {
        Map<String, Object> d = new HashMap<>();
        d.put("msgSn",     m.getMsgSn());
        d.put("roomSn",    m.getRoomSn());
        d.put("userSn",    m.getUserSn());
        boolean hidden = m.getHiddenAt() != null;   // 가려진 메시지는 내용을 아예 안 보낸다(화면이 숨기는 게 아니라)
        boolean deleted = m.getDeletedAt() != null;   // 보낸 사람이 지운 것도 마찬가지
        d.put("msgCntnt",  hidden || deleted ? "" : m.getMsgCntnt());
        d.put("imgUrl",    hidden || deleted ? null : m.getImgUrl());
        d.put("hidden",    hidden);
        d.put("deleted",   deleted);
        d.put("createdAt", m.getFrstCrtDt().format(ISO));
        return d;
    }
}
