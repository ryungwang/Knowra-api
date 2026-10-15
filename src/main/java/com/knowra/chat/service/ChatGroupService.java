package com.knowra.chat.service;

import com.knowra.cmm.i18n.Msg;
import com.knowra.chat.entity.TblChatRoom;
import com.knowra.chat.entity.TblChatRoomMbr;
import com.knowra.chat.repository.TblChatRoomMbrRepository;
import com.knowra.chat.repository.TblChatRoomRepository;
import com.knowra.safety.service.SafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 그룹 채팅 — 만들기·초대·나가기·모임 대화방.
 *
 * <p>초대받은 사람은 REQUEST(요청함)로 들어간다 — DM 과 같은 이유로, 모르는 사람이 남을 아무 방에나 넣지 못하게.
 * 모임 대화방만 참석한 사람을 바로 ACTIVE 로 넣는다(스스로 고른 모임이다). 익명 커뮤니티 모임은 대화방을 만들지
 * 않는다 — 대화방 멤버 목록이 참석자를 드러내 익명을 깬다.
 */
@Service
@RequiredArgsConstructor
public class ChatGroupService {

    public static final int MAX_MEMBERS = 30;
    public static final String GROUP = "GROUP";

    private final TblChatRoomRepository roomRepository;
    private final TblChatRoomMbrRepository mbrRepository;
    private final SafetyService safetyService;
    private final JdbcTemplate jdbc;

    @Transactional
    public long create(long me, String name, Collection<?> nickNames) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 50) throw new IllegalArgumentException("방 이름을 1~50자로 써 주세요.");
        List<Long> invitees = resolve(me, nickNames);
        if (invitees.isEmpty()) throw new IllegalArgumentException("초대할 사람을 한 명 이상 골라 주세요.");
        if (invitees.size() + 1 > MAX_MEMBERS) throw new IllegalArgumentException(Msg.t("그룹은 {0}명까지예요.", MAX_MEMBERS));
        TblChatRoom room = roomRepository.save(TblChatRoom.builder().roomTyp(GROUP).name(n).createdBy(me).build());
        mbrRepository.save(TblChatRoomMbr.builder().roomSn(room.getRoomSn()).userSn(me).build());
        for (Long u : invitees) mbrRepository.save(TblChatRoomMbr.builder().roomSn(room.getRoomSn()).userSn(u).stat("REQUEST").build());
        return room.getRoomSn();
    }

    /** 만든 사람만 초대한다. 이미 있거나 거절한 사람은 건너뛴다(거절한 걸 다시 밀어 넣지 않는다) */
    @Transactional
    public int invite(long me, long roomSn, Collection<?> nickNames) {
        TblChatRoom room = requireGroup(roomSn);
        if (room.getCreatedBy() == null || room.getCreatedBy() != me) throw new IllegalArgumentException("방을 만든 사람만 초대할 수 있어요.");
        Map<Long, TblChatRoomMbr> existing = new LinkedHashMap<>();
        mbrRepository.findAllByRoomSn(roomSn).forEach(m -> existing.put(m.getUserSn(), m));
        long live = existing.values().stream().filter(m -> Set.of("ACTIVE", "REQUEST").contains(m.getStat())).count();
        int added = 0;
        for (Long u : resolve(me, nickNames)) {
            TblChatRoomMbr m = existing.get(u);
            if (m != null && !"LEFT".equals(m.getStat())) continue;
            if (live + added + 1 > MAX_MEMBERS) throw new IllegalArgumentException(Msg.t("그룹은 {0}명까지예요.", MAX_MEMBERS));
            if (m != null) { m.setStat("REQUEST"); mbrRepository.save(m); }
            else mbrRepository.save(TblChatRoomMbr.builder().roomSn(roomSn).userSn(u).stat("REQUEST").build());
            added++;
        }
        return added;
    }

    @Transactional
    public void leave(long me, long roomSn) {
        TblChatRoom room = requireGroup(roomSn);
        TblChatRoomMbr mine = mbrRepository.findByRoomSnAndUserSn(roomSn, me).filter(m -> !"LEFT".equals(m.getStat()))
                .orElseThrow(() -> new IllegalArgumentException("참여하지 않은 대화방입니다."));
        mine.setStat("LEFT");
        mbrRepository.save(mine);
        // 만든 사람이 나가면 남은 사람 중 가장 먼저 들어온 사람이 초대를 맡는다
        if (room.getCreatedBy() != null && room.getCreatedBy() == me) {
            room.setCreatedBy(mbrRepository.findAllByRoomSn(roomSn).stream().filter(m -> "ACTIVE".equals(m.getStat()))
                    .min(java.util.Comparator.comparing(TblChatRoomMbr::getFrstCrtDt)).map(TblChatRoomMbr::getUserSn).orElse(null));
            roomRepository.save(room);
        }
    }

    /** 모임 대화방 열기 — 모임을 만든 사람이나 운영진. 이미 있으면 그 방(그 사람이 없으면 넣는다) */
    @Transactional
    public long forEvent(long me, long eventSn) {
        Map<String, Object> ev = jdbc.queryForList("""
                SELECT e.comm_sn, e.title, e.created_by, e.stat, c.prvcy_stng,
                       (SELECT role FROM knowra_community.tbl_comm_mbr m WHERE m.comm_sn = e.comm_sn AND m.user_sn = ? AND m.stat = 'ACTIVE') AS my_role
                FROM knowra_community.tbl_comm_event e JOIN knowra_community.tbl_comm c ON c.comm_sn = e.comm_sn WHERE e.event_sn = ?""", me, eventSn)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("모임을 찾을 수 없어요."));
        if ("anonymous".equals(ev.get("prvcy_stng"))) throw new IllegalArgumentException("익명 커뮤니티 모임은 대화방을 열 수 없어요 — 참석자가 드러나요.");
        if (!"ACTIVE".equals(ev.get("stat"))) throw new IllegalArgumentException("취소된 모임이에요.");
        String role = (String) ev.get("my_role");
        boolean creator = ((Number) ev.get("created_by")).longValue() == me && role != null;
        if (!creator && !"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("모임을 만든 사람이나 운영진만 대화방을 열 수 있어요.");

        TblChatRoom room = roomRepository.findByEventSn(eventSn).orElse(null);
        if (room == null) {
            String title = String.valueOf(ev.get("title"));
            String name = ("모임 · " + title).length() > 50 ? ("모임 · " + title).substring(0, 49) + "…" : "모임 · " + title;
            room = roomRepository.save(TblChatRoom.builder().roomTyp(GROUP).name(name).createdBy(me).eventSn(eventSn).build());
            Set<Long> people = new LinkedHashSet<>();
            people.add(me);
            people.addAll(jdbc.queryForList("SELECT user_sn FROM knowra_community.tbl_comm_event_rsvp WHERE event_sn = ? ORDER BY frst_crt_dt LIMIT ?",
                    Long.class, eventSn, MAX_MEMBERS));
            int n = 0;
            for (Long u : people) {
                if (n++ >= MAX_MEMBERS) break;
                mbrRepository.save(TblChatRoomMbr.builder().roomSn(room.getRoomSn()).userSn(u).build());
            }
        } else {
            join(room.getRoomSn(), me);
        }
        return room.getRoomSn();
    }

    /** 참석을 누르면 모임 대화방이 있을 때 넣는다(꽉 찼으면 넣지 않는다) */
    @Transactional
    public void onRsvp(long eventSn, long userSn, boolean going) {
        if (!going) return;
        roomRepository.findByEventSn(eventSn).ifPresent(room -> join(room.getRoomSn(), userSn));
    }

    private void join(long roomSn, long userSn) {
        TblChatRoomMbr m = mbrRepository.findByRoomSnAndUserSn(roomSn, userSn).orElse(null);
        if (m != null && "ACTIVE".equals(m.getStat())) return;
        long live = mbrRepository.findAllByRoomSn(roomSn).stream().filter(x -> "ACTIVE".equals(x.getStat())).count();
        if (live >= MAX_MEMBERS) return;
        if (m != null) { m.setStat("ACTIVE"); mbrRepository.save(m); }
        else mbrRepository.save(TblChatRoomMbr.builder().roomSn(roomSn).userSn(userSn).build());
    }

    /** 방 멤버(나간 사람 빼고) — 화면이 보낸 사람 이름·사진을 그린다 */
    public List<Map<String, Object>> members(long roomSn) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT m.user_sn, m.stat, u.nick_name, u.name, f.atch_file_path_nm, f.strg_file_nm, f.atch_file_extn_nm, f.file_key
                FROM knowra_user.tbl_chat_room_mbr m JOIN knowra_user.tbl_user u ON u.user_sn = m.user_sn
                LEFT JOIN knowra_com.tbl_com_file f ON f.atch_file_sn = u.atch_file_sn
                WHERE m.room_sn = ? AND m.stat IN ('ACTIVE','REQUEST') ORDER BY m.frst_crt_dt""", roomSn)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userSn", r.get("user_sn")); m.put("stat", r.get("stat"));
            m.put("nickName", r.get("nick_name")); m.put("name", r.get("name"));
            m.put("pfpUrl", com.knowra.cmm.storage.StorageUrls.of((String) r.get("atch_file_path_nm"), (String) r.get("strg_file_nm"), (String) r.get("atch_file_extn_nm"), (String) r.get("file_key")));
            out.add(m);
        }
        return out;
    }

    private TblChatRoom requireGroup(long roomSn) {
        TblChatRoom room = roomRepository.findById(roomSn).orElseThrow(() -> new IllegalArgumentException("대화방을 찾을 수 없어요."));
        if (!GROUP.equals(room.getRoomTyp())) throw new IllegalArgumentException("그룹 대화방이 아니에요.");
        return room;
    }

    /** 닉네임 → 사용자. 나·없는 사람·정지·탈퇴·나와 차단 관계인 사람은 뺀다 */
    private List<Long> resolve(long me, Collection<?> nickNames) {
        List<Long> out = new ArrayList<>();
        if (nickNames == null) return out;
        for (Object o : new LinkedHashSet<>(nickNames)) {
            Long sn = jdbc.query("SELECT user_sn FROM knowra_user.tbl_user WHERE nick_name = ? AND actvtn_yn = 'Y' AND status <> 'S'",
                    rs -> rs.next() ? rs.getLong(1) : null, String.valueOf(o));
            if (sn == null || sn == me || safetyService.blockedEitherWay(me, sn)) continue;
            out.add(sn);
        }
        return out;
    }
}
