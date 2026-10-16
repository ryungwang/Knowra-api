package com.knowra.chat.service;

import com.knowra.cmm.i18n.Msg;
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
    private final org.springframework.context.ApplicationEventPublisher events;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 받는 반응 — 고른 몇 가지만(아무 글자나 받으면 반응이 또 하나의 메시지 창구가 된다) */
    static final List<String> REACTIONS = List.of("👍", "❤️", "😂", "😮", "😢", "🙏");
    /** 입력 중 신호를 같은 사람·방에서 이 간격보다 자주 흘리지 않는다 */
    private static final long TYPING_GAP_MS = 2500;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> typingAt = new java.util.concurrent.ConcurrentHashMap<>();

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
                    row.put("group", Map.of("name", room.getName() == null ? Msg.t("그룹") : room.getName(), "memberCnt", cnt));
                } else {
                    row.put("peer",  peerByRoom.get(room.getRoomSn()));
                }
                row.put("lastMsg",   last == null ? null : last.getDeletedAt() != null ? Msg.t("삭제된 메시지") : last.getHiddenAt() != null ? Msg.t("가려진 메시지") : (last.getMsgCntnt().isEmpty() && last.getImgUrl() != null ? Msg.t("사진") : last.getMsgCntnt()));
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
            List<Map<String, Object>> list = decorate(rows.stream().filter(x -> !blocked.contains(x.getUserSn())).map(this::toDto).toList());
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

            // 답장 — 같은 방의 메시지에만(다른 방 메시지 번호로 그 내용을 떠보지 못하게)
            Long replyTo = params.get("replyToMsgSn") == null || String.valueOf(params.get("replyToMsgSn")).isBlank() ? null : Long.parseLong(String.valueOf(params.get("replyToMsgSn")));
            if (replyTo != null && msgRepository.findById(replyTo).filter(x -> x.getRoomSn() == roomSn).isEmpty())
                throw new IllegalArgumentException("답장할 수 없는 메시지예요.");
            TblChatMsg saved = msgRepository.save(TblChatMsg.builder().roomSn(roomSn).userSn(userSn).msgCntnt(text).imgUrl(imgUrl).replyToMsgSn(replyTo).build());
            TblChatRoom room = roomRepository.findById(roomSn).orElseThrow();
            room.setLastMsgSn(saved.getMsgSn());
            room.setLastMsgAt(saved.getFrstCrtDt());
            roomRepository.save(room);
            // 내가 보낸 건 내가 읽은 것이다
            roomMbrRepository.findByRoomSnAndUserSn(roomSn, userSn).ifPresent(mb -> {
                mb.setLastReadMsgSn(saved.getMsgSn()); roomMbrRepository.save(mb);
            });

            Map<String, Object> dto = decorate(List.of(toDto(saved))).get(0);
            Map<Long, Boolean> recipients = new LinkedHashMap<>();
            for (TblChatRoomMbr mb : roomMbrRepository.findAllByRoomSn(roomSn)) {
                if (mb.getUserSn() == userSn || "DECLINED".equals(mb.getStat()) || "LEFT".equals(mb.getStat())) continue;   // 거절·나간 사람에겐 아무것도 안 간다
                if (group && safetyService.blockedEitherWay(userSn, mb.getUserSn())) continue;
                Map<String, Object> ev = new HashMap<>(dto);
                ev.put("request", "REQUEST".equals(mb.getStat()));   // 요청함 쪽은 배지만, 대화 목록·안 읽은 수엔 안 섞는다
                wsHandler.pushEvent(mb.getUserSn(), "CHAT", ev);
                recipients.put(mb.getUserSn(), "REQUEST".equals(mb.getStat()));
            }
            // 접속하지 않은 사람에겐 웹 푸시 — 커밋 뒤에(ChatPushListener)
            if (!recipients.isEmpty())
                events.publishEvent(new ChatMsgSentEvent(roomSn, userSn, text, imgUrl != null, group, room.getName(), recipients));
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
        d.put("replyToMsgSn", m.getReplyToMsgSn());
        return d;
    }

    /**
     * 메시지들에 답장 대상 요약(replyTo)과 반응(reactions: [{ emoji, users }])을 붙인다 — 한 번에 읽는다.
     * 답장 대상이 지워졌거나 가려졌으면 내용 없이 표시만(원 메시지와 같은 규칙).
     */
    List<Map<String, Object>> decorate(List<Map<String, Object>> list) {
        if (list.isEmpty()) return list;
        List<Long> sns = list.stream().map(d -> ((Number) d.get("msgSn")).longValue()).toList();
        List<Long> replySns = list.stream().map(d -> (Long) d.get("replyToMsgSn")).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, List<Map<String, Object>>> reacts = reactionsOf(sns);
        Map<Long, Map<String, Object>> replies = new HashMap<>();
        if (!replySns.isEmpty())
            for (TblChatMsg r : msgRepository.findAllById(replySns)) {
                Map<String, Object> o = new HashMap<>();
                boolean gone = r.getHiddenAt() != null || r.getDeletedAt() != null;
                o.put("msgSn", r.getMsgSn());
                o.put("userSn", r.getUserSn());
                o.put("gone", gone);
                String c = gone ? "" : r.getMsgCntnt();
                o.put("msgCntnt", c.length() > 80 ? c.substring(0, 79) + "…" : c);
                o.put("hasImg", !gone && r.getImgUrl() != null);
                replies.put(r.getMsgSn(), o);
            }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Map<String, Object> d : list) {
            Map<String, Object> x = new HashMap<>(d);
            long sn = ((Number) d.get("msgSn")).longValue();
            x.put("reactions", reacts.getOrDefault(sn, List.of()));
            Long r = (Long) d.get("replyToMsgSn");
            if (r != null) x.put("replyTo", replies.getOrDefault(r, Map.of("msgSn", r, "gone", true)));
            out.add(x);
        }
        return out;
    }

    private Map<Long, List<Map<String, Object>>> reactionsOf(List<Long> msgSns) {
        Map<Long, Map<String, List<Long>>> acc = new HashMap<>();
        if (msgSns.isEmpty()) return Map.of();
        String in = String.join(",", java.util.Collections.nCopies(msgSns.size(), "?"));
        jdbc.query("SELECT msg_sn, emoji, user_sn FROM knowra_user.tbl_chat_msg_react WHERE msg_sn IN (" + in + ") ORDER BY created_at", rs -> {
            acc.computeIfAbsent(rs.getLong(1), k -> new LinkedHashMap<>()).computeIfAbsent(rs.getString(2), k -> new ArrayList<>()).add(rs.getLong(3));
        }, msgSns.toArray());
        Map<Long, List<Map<String, Object>>> out = new HashMap<>();
        acc.forEach((sn, byEmoji) -> out.put(sn, byEmoji.entrySet().stream()
                .map(e -> Map.<String, Object>of("emoji", e.getKey(), "users", e.getValue())).toList()));
        return out;
    }

    // ── 반응 ─────────────────────────────────────────────────────────────
    /** params: msgSn, emoji, on — 방 멤버만. 바뀐 반응을 방 사람들에게 밀어 준다(CHAT_REACT) */
    @Transactional
    public ResultVO react(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long msgSn = Long.parseLong(String.valueOf(params.get("msgSn")));
            String emoji = String.valueOf(params.get("emoji"));
            boolean on = Boolean.parseBoolean(String.valueOf(params.get("on")));
            if (!REACTIONS.contains(emoji)) throw new IllegalArgumentException("잘못된 요청이에요.");
            TblChatMsg m = msgRepository.findById(msgSn).orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없어요."));
            requireMember(m.getRoomSn(), me);
            if (m.getDeletedAt() != null || m.getHiddenAt() != null) throw new IllegalArgumentException("메시지를 찾을 수 없어요.");
            if (on) jdbc.update("INSERT INTO knowra_user.tbl_chat_msg_react (msg_sn, user_sn, emoji) VALUES (?,?,?) ON CONFLICT DO NOTHING", msgSn, me, emoji);
            else jdbc.update("DELETE FROM knowra_user.tbl_chat_msg_react WHERE msg_sn = ? AND user_sn = ? AND emoji = ?", msgSn, me, emoji);
            List<Map<String, Object>> reactions = reactionsOf(List.of(msgSn)).getOrDefault(msgSn, List.of());
            Map<String, Object> ev = Map.of("roomSn", m.getRoomSn(), "msgSn", msgSn, "reactions", reactions);
            for (TblChatRoomMbr mb : roomMbrRepository.findAllByRoomSn(m.getRoomSn()))
                if ("ACTIVE".equals(mb.getStat()) && mb.getUserSn() != me) wsHandler.pushEvent(mb.getUserSn(), "CHAT_REACT", ev);
            resultVO.putResult("reactions", reactions);
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

    // ── 입력 중 ──────────────────────────────────────────────────────────
    /**
     * 웹소켓 TYPING 을 방의 다른 사람들에게 흘린다(저장하지 않는다). 수락한 멤버끼리만 — 요청함 상대에게는 안 간다.
     * 같은 사람·방은 TYPING_GAP_MS 안에 한 번만(화면이 자주 보내도 서버가 거른다).
     */
    @org.springframework.context.event.EventListener
    public void onTyping(com.knowra.cmm.handler.ChatTypingEvent e) {
        String key = e.userSn() + ":" + e.roomSn();
        long now = System.currentTimeMillis();
        Long last = typingAt.get(key);
        if (last != null && now - last < TYPING_GAP_MS) return;
        typingAt.put(key, now);
        if (typingAt.size() > 10_000) typingAt.clear();
        List<TblChatRoomMbr> mbs = roomMbrRepository.findAllByRoomSn(e.roomSn());
        if (mbs.stream().noneMatch(m -> m.getUserSn() == e.userSn() && "ACTIVE".equals(m.getStat()))) return;
        Map<String, Object> ev = Map.of("roomSn", e.roomSn(), "userSn", e.userSn());
        for (TblChatRoomMbr mb : mbs)
            if (mb.getUserSn() != e.userSn() && "ACTIVE".equals(mb.getStat()) && !safetyService.blockedEitherWay(e.userSn(), mb.getUserSn()))
                wsHandler.pushEvent(mb.getUserSn(), "CHAT_TYPING", ev);
    }

    // ── 대화 안 검색 ─────────────────────────────────────────────────────
    /** params: roomSn, q(2~50자) — 이 방에서 글자가 들어간 메시지, 최근 것부터 30개. 지운·가린 메시지와 차단한 사람 것은 뺀다 */
    @Transactional(readOnly = true)
    public ResultVO searchMsg(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long roomSn = Long.parseLong(String.valueOf(params.get("roomSn")));
            String q = params.get("q") == null ? "" : String.valueOf(params.get("q")).trim();
            if (q.length() < 2 || q.length() > 50) throw new IllegalArgumentException("두 글자 이상으로 찾아 주세요.");
            requireMember(roomSn, me);
            String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            Set<Long> blocked = new HashSet<>();
            for (TblChatRoomMbr x : roomMbrRepository.findAllByRoomSn(roomSn))
                if (x.getUserSn() != me && safetyService.blockedEitherWay(me, x.getUserSn())) blocked.add(x.getUserSn());
            List<Long> ids = jdbc.queryForList("""
                    SELECT msg_sn FROM knowra_user.tbl_chat_msg
                    WHERE room_sn = ? AND deleted_at IS NULL AND hidden_at IS NULL AND msg_cntnt ILIKE ? ESCAPE '\\'
                    ORDER BY msg_sn DESC LIMIT 30""", Long.class, roomSn, like);
            List<Map<String, Object>> list = msgRepository.findAllById(ids).stream()
                    .filter(x -> !blocked.contains(x.getUserSn()))
                    .sorted(java.util.Comparator.comparing(TblChatMsg::getMsgSn).reversed())
                    .map(this::toDto).toList();
            resultVO.putResult("list", list);
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
}
