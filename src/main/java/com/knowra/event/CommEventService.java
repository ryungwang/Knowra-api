package com.knowra.event;

import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.common.entity.QTblComFile;
import com.knowra.community.entity.QTblCommMbr;
import com.knowra.community.entity.TblComm;
import com.knowra.community.entity.TblCommMbr;
import com.knowra.community.repository.TblCommMbrRepository;
import com.knowra.user.entity.QTblUser;
import com.knowra.wiki.WikiService;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 커뮤니티 모임. 보기: 커뮤니티를 볼 수 있는 사람(비공개면 멤버). 만들기·참석: 활성 멤버.
 * 고치기·취소: 만든 사람이나 운영진. 알림: 새 모임 → 새 글 알림을 켠 멤버, 하루 전·한 시간 전 → 참석자,
 * 취소 → 참석자. 리마인더는 스케줄러가 5분마다 보고 한 번만 보낸다.
 */
@Service
@RequiredArgsConstructor
public class CommEventService {

    @PersistenceContext
    private EntityManager em;
    private final CommEventRepository events;
    private final CommEventRsvpRepository rsvps;
    private final TblCommMbrRepository mbrs;
    private final WikiService wiki;   // 보기 권한(비공개 커뮤니티) 판정을 같이 쓴다
    private final ApplicationEventPublisher publisher;

    private String activeRole(long commSn, Long me) {
        if (me == null) return null;
        return mbrs.findByCommSnAndUserSn(commSn, me).filter(m -> "ACTIVE".equals(m.getStat()) && "Y".equals(m.getActvtnYn()))
                .map(TblCommMbr::getRole).orElse(null);
    }

    public List<Map<String, Object>> list(long commSn, String scope, Long me) {
        wiki.requireReadable(commSn, me);
        LocalDateTime now = LocalDateTime.now();
        // 진행 중인 모임(시작했지만 안 끝난)도 「다가오는」에 남긴다 — 끝 시간이 없으면 시작 뒤 3시간까지
        List<TblCommEvent> rows = "past".equals(scope)
                ? events.findTop20ByCommSnAndStartsAtBeforeOrderByStartsAtDesc(commSn, now.minusHours(3))
                : events.findAllByCommSnAndStartsAtAfterOrderByStartsAtAsc(commSn, now.minusHours(3)).stream()
                    .filter(e -> e.getEndsAt() == null ? e.getStartsAt().plusHours(3).isAfter(now) : e.getEndsAt().isAfter(now)).toList();
        Map<Long, List<TblCommEventRsvp>> byEvent = new HashMap<>();
        if (!rows.isEmpty()) rsvps.findAllByEventSnIn(rows.stream().map(TblCommEvent::getEventSn).toList())
                .forEach(r -> byEvent.computeIfAbsent(r.getEventSn(), k -> new ArrayList<>()).add(r));
        String role = activeRole(commSn, me);
        List<Map<String, Object>> out = new ArrayList<>();
        for (TblCommEvent e : rows) out.add(toDto(e, byEvent.getOrDefault(e.getEventSn(), List.of()), me, role));
        return out;
    }

    public Map<String, Object> detail(long eventSn, Long me) {
        TblCommEvent e = events.findById(eventSn).orElseThrow(() -> new IllegalArgumentException("모임을 찾을 수 없어요."));
        wiki.requireReadable(e.getCommSn(), me);
        List<TblCommEventRsvp> rs = rsvps.findAllByEventSnOrderByFrstCrtDtAsc(eventSn);
        Map<String, Object> m = toDto(e, rs, me, activeRole(e.getCommSn(), me));
        m.put("attendees", attendees(rs));
        return m;
    }

    private List<Map<String, Object>> attendees(List<TblCommEventRsvp> rs) {
        if (rs.isEmpty()) return List.of();
        QTblUser u = QTblUser.tblUser;
        QTblComFile pfp = new QTblComFile("pfp");
        Map<Long, Map<String, Object>> byUser = new HashMap<>();
        new JPAQueryFactory(em).select(u.userSn, u.nickName, u.name, pfp.atchFilePathNm, pfp.strgFileNm, pfp.atchFileExtnNm)
                .from(u).leftJoin(u.pfp, pfp).where(u.userSn.in(rs.stream().map(TblCommEventRsvp::getUserSn).toList())).fetch()
                .forEach(t -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("nickName", t.get(u.nickName)); x.put("name", t.get(u.name));
                    String path = t.get(pfp.atchFilePathNm);
                    x.put("pfpUrl", path == null ? null : path + "/" + t.get(pfp.strgFileNm) + "." + t.get(pfp.atchFileExtnNm));
                    byUser.put(t.get(u.userSn), x);
                });
        return rs.stream().map(r -> byUser.get(r.getUserSn())).filter(Objects::nonNull).toList();
    }

    private Map<String, Object> toDto(TblCommEvent e, List<TblCommEventRsvp> rs, Long me, String myRole) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventSn", e.getEventSn()); m.put("commSn", e.getCommSn()); m.put("title", e.getTitle()); m.put("descr", e.getDescr());
        m.put("startsAt", e.getStartsAt()); m.put("endsAt", e.getEndsAt()); m.put("place", e.getPlace()); m.put("onlineUrl", e.getOnlineUrl());
        m.put("capacity", e.getCapacity()); m.put("stat", e.getStat()); m.put("goingCnt", rs.size());
        m.put("going", me != null && rs.stream().anyMatch(r -> r.getUserSn() == me));
        m.put("canEdit", me != null && (e.getCreatedBy() == me || "OWNER".equals(myRole) || "ADMIN".equals(myRole)));
        m.put("past", e.getStartsAt().isBefore(LocalDateTime.now()));
        return m;
    }

    /** body: commSn, eventSn?(고치기), title, descr, startsAt(ISO), endsAt?, place?, onlineUrl?, capacity? */
    @Transactional
    public Map<String, Object> save(Map<String, Object> b, long me) {
        long commSn = Long.parseLong(String.valueOf(b.get("commSn")));
        TblComm comm = wiki.requireReadable(commSn, me);
        String role = activeRole(commSn, me);
        if (role == null) throw new IllegalArgumentException("멤버만 모임을 만들 수 있어요.");
        String title = str(b.get("title")).trim(), descr = str(b.get("descr")).trim();
        if (title.isEmpty() || title.length() > 80) throw new IllegalArgumentException("모임 이름을 1~80자로 써 주세요.");
        if (descr.length() > 2000) throw new IllegalArgumentException("설명은 2000자까지예요.");
        LocalDateTime starts = time(b.get("startsAt"));
        if (starts == null) throw new IllegalArgumentException("시작 시간을 골라 주세요.");
        LocalDateTime ends = time(b.get("endsAt"));
        if (ends != null && !ends.isAfter(starts)) throw new IllegalArgumentException("끝나는 시간은 시작보다 뒤여야 해요.");
        String place = blankToNull(str(b.get("place")).trim()), url = blankToNull(str(b.get("onlineUrl")).trim());
        if (place != null && place.length() > 120) throw new IllegalArgumentException("장소는 120자까지예요.");
        if (url != null && (url.length() > 300 || !url.matches("^https?://\\S+$"))) throw new IllegalArgumentException("온라인 링크는 http(s) 주소만 넣을 수 있어요.");
        Integer cap = b.get("capacity") == null || str(b.get("capacity")).isBlank() ? null : Integer.valueOf(str(b.get("capacity")));
        if (cap != null && (cap < 1 || cap > 10000)) throw new IllegalArgumentException("정원은 1~10000명이에요.");

        boolean isNew = b.get("eventSn") == null;
        TblCommEvent e;
        if (isNew) {
            if (starts.isBefore(LocalDateTime.now())) throw new IllegalArgumentException("지난 시간으로는 모임을 만들 수 없어요.");
            e = TblCommEvent.builder().commSn(commSn).createdBy(me).build();
        } else {
            e = events.findById(Long.valueOf(str(b.get("eventSn")))).filter(x -> x.getCommSn() == commSn)
                    .orElseThrow(() -> new IllegalArgumentException("모임을 찾을 수 없어요."));
            if (e.getCreatedBy() != me && !"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("만든 사람이나 운영진만 고칠 수 있어요.");
            if (!e.getStartsAt().equals(starts)) { e.setRemindedDay(false); e.setRemindedHour(false); }   // 시간이 바뀌면 리마인더를 다시
        }
        e.setTitle(title); e.setDescr(descr); e.setStartsAt(starts); e.setEndsAt(ends); e.setPlace(place); e.setOnlineUrl(url); e.setCapacity(cap);
        events.save(e);
        if (isNew) {
            // 만든 사람은 당연히 참석
            rsvps.save(TblCommEventRsvp.builder().eventSn(e.getEventSn()).userSn(me).build());
            notifyMembers(commSn, me, "EVENT_NEW", e, comm);
        }
        return detail(e.getEventSn(), me);
    }

    /** 참석 / 취소. body: eventSn, going */
    @Transactional
    public Map<String, Object> rsvp(long eventSn, boolean going, long me) {
        TblCommEvent e = events.findById(eventSn).orElseThrow(() -> new IllegalArgumentException("모임을 찾을 수 없어요."));
        if (activeRole(e.getCommSn(), me) == null) throw new IllegalArgumentException("멤버만 참석할 수 있어요.");
        if (!"ACTIVE".equals(e.getStat())) throw new IllegalArgumentException("취소된 모임이에요.");
        if (e.getStartsAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("이미 시작한 모임이에요.");
        Optional<TblCommEventRsvp> mine = rsvps.findByEventSnAndUserSn(eventSn, me);
        if (going && mine.isEmpty()) {
            if (e.getCapacity() != null && rsvps.countByEventSn(eventSn) >= e.getCapacity()) throw new IllegalArgumentException("정원이 찼어요.");
            rsvps.save(TblCommEventRsvp.builder().eventSn(eventSn).userSn(me).build());
        } else if (!going) mine.ifPresent(rsvps::delete);
        return detail(eventSn, me);
    }

    @Transactional
    public Map<String, Object> cancel(long eventSn, long me) {
        TblCommEvent e = events.findById(eventSn).orElseThrow(() -> new IllegalArgumentException("모임을 찾을 수 없어요."));
        String role = activeRole(e.getCommSn(), me);
        if (e.getCreatedBy() != me && !"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("만든 사람이나 운영진만 취소할 수 있어요.");
        if (!"CANCELLED".equals(e.getStat())) {
            e.setStat("CANCELLED");
            events.save(e);
            for (TblCommEventRsvp r : rsvps.findAllByEventSnOrderByFrstCrtDtAsc(eventSn)) {
                publisher.publishEvent(new NotifTriggerEvent(r.getUserSn(), me, "EVENT_CANCEL", e.getCommSn(), "COMM", e.getTitle()));
            }
        }
        return detail(eventSn, me);
    }

    private void notifyMembers(long commSn, long sender, String typ, TblCommEvent e, TblComm comm) {
        QTblCommMbr m = QTblCommMbr.tblCommMbr;
        List<Long> to = new JPAQueryFactory(em).select(m.userSn).from(m)
                .where(m.commSn.eq(commSn), m.stat.eq("ACTIVE"), m.actvtnYn.eq("Y"), m.notifYn.eq("Y"), m.userSn.ne(sender)).fetch();
        String label = (comm.getCommDsplNm() != null ? comm.getCommDsplNm() : comm.getCommNm()) + "|" + e.getTitle();
        for (Long u : to) publisher.publishEvent(new NotifTriggerEvent(u, sender, typ, commSn, "COMM", label));
    }

    /** 리마인더 — 5분마다. 하루 전(23~24시간 남음)·한 시간 전(0~60분 남음) 참석자에게 한 번씩. */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    @Transactional
    public void remind() {
        LocalDateTime now = LocalDateTime.now();
        for (TblCommEvent e : events.findAllByStatAndStartsAtBetween("ACTIVE", now, now.plusHours(24))) {
            long minutes = java.time.Duration.between(now, e.getStartsAt()).toMinutes();
            String typ = null;
            if (minutes <= 60 && !e.isRemindedHour()) { typ = "EVENT_SOON"; e.setRemindedHour(true); e.setRemindedDay(true); }
            else if (minutes > 60 && !e.isRemindedDay()) { typ = "EVENT_TOMORROW"; e.setRemindedDay(true); }
            if (typ == null) continue;
            events.save(e);
            for (TblCommEventRsvp r : rsvps.findAllByEventSnOrderByFrstCrtDtAsc(e.getEventSn())) {
                publisher.publishEvent(new NotifTriggerEvent(r.getUserSn(), null, typ, e.getCommSn(), "COMM", e.getTitle()));
            }
        }
    }

    private static LocalDateTime time(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        String s = String.valueOf(o).trim();
        try { return LocalDateTime.parse(s.length() == 16 ? s + ":00" : s.replaceFirst("Z$", "")); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("시간 형식이 올바르지 않아요."); }
    }
    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String blankToNull(String s) { return s == null || s.isEmpty() ? null : s; }
}
