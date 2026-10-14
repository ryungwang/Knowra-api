package com.knowra.post.service;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.service.TagService;
import com.knowra.community.entity.QTblComm;
import com.knowra.community.entity.QTblCommMbr;
import com.knowra.community.entity.TblComm;
import com.knowra.community.entity.TblCommMbr;
import com.knowra.community.repository.TblCommMbrRepository;
import com.knowra.community.repository.TblCommRepository;
import com.knowra.post.entity.CmtDTO;
import com.knowra.post.entity.PostDTO;
import com.knowra.post.entity.QTblPost;
import com.knowra.post.entity.QTblPostCmt;
import com.knowra.post.entity.QTblPostSave;
import com.knowra.post.entity.QTblPostTag;
import com.knowra.post.entity.TblPost;
import com.knowra.post.entity.TblPostCmt;
import com.knowra.post.entity.TblPostCmtReact;
import com.knowra.post.entity.TblPostLike;
import com.knowra.post.entity.TblPostSave;
import com.knowra.post.event.PostCreatedEvent;
import com.knowra.post.event.PostDeletedEvent;
import com.knowra.post.event.PostReactedEvent;
import com.knowra.post.repository.TblPostCmtReactRepository;
import com.knowra.post.repository.TblPostCmtRepository;
import com.knowra.post.repository.TblPostLikeRepository;
import com.knowra.post.repository.TblPostRepository;
import com.knowra.post.repository.TblPostSaveRepository;
import com.knowra.user.entity.QTblUser;
import com.knowra.user.entity.TblUserActionLog;
import com.knowra.user.service.ActionLogService;
import com.knowra.user.service.InterestScoreService;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.knowra.post.service.PostQuery.POST;

/**
 * 게시글 — 전체 공개 글과 커뮤니티 글을 한 곳에서 다룬다.
 *
 * <p>글 모델을 합치기 전에는 {@code CommunityPostService} 가 이 클래스의 거의 완전한 복사본이었다
 * (엔티티 이름만 다른 좋아요·댓글·반응·목록 로직). 이제 커뮤니티 글은 {@code commSn} 이 붙은
 * 보통 글이고, 커뮤니티에만 있는 것은 게시판 목록({@link #getCommPostList})과 관리자 권한뿐이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    @PersistenceContext
    private EntityManager em;

    private final PostQuery postQuery;
    private final TagService tagService;
    private final TblPostRepository tblPostRepository;
    private final TblPostLikeRepository tblPostLikeRepository;
    private final TblPostCmtRepository tblPostCmtRepository;
    private final TblPostCmtReactRepository tblPostCmtReactRepository;
    private final TblPostSaveRepository tblPostSaveRepository;
    private final com.knowra.savefolder.TblPostSaveFolderRepository saveFolderRepository;
    private final TblCommRepository tblCommRepository;
    private final TblCommMbrRepository tblCommMbrRepository;
    private final ModelMapper modelMapper;
    private final RedisApiService redisApiService;
    private final ActionLogService actionLogService;
    private final InterestScoreService interestScoreService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.knowra.poll.service.PollService pollService;
    private final com.knowra.mention.MentionService mentionService;
    private final com.knowra.badge.BadgeService badgeService;
    private final com.knowra.community.modlog.ModLogService modLog;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.knowra.keyword.KeywordService keywordService;
    private final com.knowra.community.flair.FlairService flairService;

    private static final int REDIS_DB  = 15;
    private static final int PAGE_SIZE = 50;

    // ─────────────────────────────────────────────────────────────────────
    // 권한
    // ─────────────────────────────────────────────────────────────────────

    /** 커뮤니티에서 viewer 의 역할(OWNER / ADMIN / MEMBER). 멤버가 아니면 null. */
    private String roleIn(Long commSn, Long userSn) {
        if (commSn == null || userSn == null) return null;
        return tblCommMbrRepository.findByCommSnAndUserSn(commSn, userSn)
                .filter(m -> "ACTIVE".equals(m.getStat()) && "Y".equals(m.getActvtnYn()))
                .map(TblCommMbr::getRole)
                .orElse(null);
    }

    private static boolean isManagerRole(String role) {
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }

    /**
     * 글을 고치거나 지울 수 있는가 — 작성자 본인, 또는 그 커뮤니티의 OWNER/ADMIN.
     *
     * <p>예전 코드는 이 검사가 없어서 postSn 만 알면 남의 글을 고칠 수 있었다. 화면은
     * 원래부터 이 규칙(canManage)으로 버튼을 숨기고 있었으니 서버가 그 규칙을 따라간다.
     */
    private boolean canManage(TblPost post, long userSn) {
        return post.getUserSn() == userSn || isManagerRole(roleIn(post.getCommSn(), userSn));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 작성 · 수정
    // ─────────────────────────────────────────────────────────────────────

    /**
     * params: postTtl, postCntnt, tagNms, [commSn] (커뮤니티 글), [postTyp] (NOTICE 는 커뮤니티
     * 관리자만), [postSn] (있으면 수정).
     */
    public ResultVO setPost(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            boolean isEdit = params.get("postSn") != null;
            // 투표는 새 글에서만. 저장 전에 검사해야 글만 남고 투표가 빠지는 일이 없다.
            com.knowra.poll.service.PollService.Draft poll = isEdit ? null : pollService.validate(params.get("poll"));
            TblPost tblPost;
            if (isEdit) {
                tblPost = tblPostRepository.findById(Long.parseLong(params.get("postSn").toString()))
                        .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
                if (!canManage(tblPost, userSn)) {
                    resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                    resultVO.setResultMessage("수정 권한이 없습니다.");
                    return resultVO;
                }
                // 운영진이 남의 글을 고쳤으면 운영 기록에 — 고치기 전 제목으로 남긴다
                if (tblPost.getUserSn() != userSn && tblPost.getCommSn() != null)
                    modLog.record(tblPost.getCommSn(), userSn, com.knowra.community.modlog.ModLogService.POST_EDIT, tblPost.getUserSn(), tblPost.getPostSn(), tblPost.getPostTtl());
                // updatable=false 컬럼(creatrSn, frstCrtDt, userSn)은 건드리지 않는다. 소속 커뮤니티도 바꾸지 않는다.
                String newTtl = params.get("postTtl").toString();
                String newCntnt = com.knowra.cmm.util.HtmlSanitizer.clean(params.get("postCntnt").toString());
                // 제목·본문이 실제로 바뀌면 고치기 전 판을 남긴다 — 「수정됨」과 이전 내용 보기의 근거
                if (!newTtl.equals(tblPost.getPostTtl()) || !newCntnt.equals(tblPost.getPostCntnt()))
                    jdbc.update("INSERT INTO knowra_post.tbl_post_hist (post_sn, post_ttl, post_cntnt, edited_by) VALUES (?, ?, ?, ?)",
                            tblPost.getPostSn(), tblPost.getPostTtl(), tblPost.getPostCntnt() == null ? "" : tblPost.getPostCntnt(), userSn);
                tblPost.setPostTtl(newTtl);
                tblPost.setPostCntnt(newCntnt);
                if (params.containsKey("flairSn")) tblPost.setFlairSn(flairService.validFor(params.get("flairSn"), tblPost.getCommSn()));
                tblPost.setPostTyp(resolvePostTyp(params, tblPost.getCommSn(), userSn, tblPost.getPostTyp()));
                tblPost.setMdfrSn(userSn);
                if (STAT_SCHEDULED.equals(tblPost.getStat())) resultVO.putResult("rsrvAt", tblPost.getRsrvAt());   // 수정해도 예약은 그대로
            } else {
                tblPost = modelMapper.map(params, TblPost.class);
                tblPost.setPostCntnt(com.knowra.cmm.util.HtmlSanitizer.clean(tblPost.getPostCntnt()));   // XSS — HtmlSanitizer 주석
                // 커뮤니티 글은 활성 멤버만 쓴다. 예전엔 이 검사가 없어 가입 대기(PENDING)·비멤버도
                // API 로는 아무 커뮤니티에나 글을 넣을 수 있었다.
                if (tblPost.getCommSn() != null && roleIn(tblPost.getCommSn(), userSn) == null) {
                    resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                    resultVO.setResultMessage("커뮤니티에 참여한 멤버만 글을 쓸 수 있습니다.");
                    return resultVO;
                }
                tblPost.setUserSn(userSn);
                tblPost.setCreatrSn(userSn);
                tblPost.setPostTyp(resolvePostTyp(params, tblPost.getCommSn(), userSn, "NORMAL"));
                // ⚠️ 본문을 엔티티에 통째로 매핑하므로 서버가 정하는 값은 여기서 다시 박는다. 예전엔 likeCnt·viewCnt 를
                // 실어 보내면 그대로 저장돼 인기 피드를 조작할 수 있었다(좋아요 9999 로 재현). 고정은 setPostPin 으로만.
                tblPost.setLikeCnt(0); tblPost.setViewCnt(0); tblPost.setCmtCnt(0);
                tblPost.setActvtnYn("Y"); tblPost.setPinnedAt(null);
                tblPost.setFlairSn(flairService.validFor(params.get("flairSn"), tblPost.getCommSn()));   // 남의 커뮤니티 말머리는 못 단다
                tblPost.setStat("ACTIVE"); tblPost.setRsrvAt(null);
                tblPost.setFrstCrtDt(java.time.LocalDateTime.now());
                java.time.LocalDateTime rsrvAt = parseReservation(params.get("rsrvAt"));
                if (rsrvAt != null) { tblPost.setStat(STAT_SCHEDULED); tblPost.setRsrvAt(rsrvAt); }
                tblPostRepository.save(tblPost);
                if (poll != null) pollService.create(tblPost.getPostSn(), poll);
                if (rsrvAt == null) publishEffects(tblPost, userSn);   // 예약 글은 올라갈 때 낸다
                else resultVO.putResult("rsrvAt", rsrvAt);
            }

            @SuppressWarnings("unchecked")
            List<String> tagNms = (List<String>) params.get("tagNms");
            if (isEdit) tagService.updateTag(tagNms, userSn, tblPost.getPostSn());
            else        tagService.setTag(tagNms, userSn, tblPost.getPostSn());

            resultVO.putResult("postSn", tblPost.getPostSn());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
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

    /**
     * 이 사람이 이 글을 볼 수 있나 — 상세 화면(getPost)과 같은 기준(살아 있음 + 비공개 커뮤니티·차단·숨김).
     * ⚠️ 글 번호를 받는 모든 창구가 이걸 거쳐야 한다. 예전엔 상세만 막혀 있어 목록·댓글·저장·숨김 창구로
     * 비공개 커뮤니티 글과 댓글을 로그인 없이도 읽을 수 있었다.
     */
    public boolean canSeePost(long postSn, Long me) {
        return postQuery.base().where(POST.postSn.eq(postSn), PostQuery.alive(), PostQuery.visibleTo(me)).fetchFirst() != null;
    }

    /** 읽기 전용 경로(댓글 목록) — 신고가 몰려 가려진 자기 글은 쓴 사람이 계속 본다. 반응·댓글 쓰기는 canSeePost 로 막는다 */
    private boolean canReadPost(long postSn, Long me) {
        if (canSeePost(postSn, me)) return true;
        return me != null && tblPostRepository.findById(postSn)
                .filter(p -> p.getUserSn() == me && "Y".equals(p.getActvtnYn()) && com.knowra.safety.service.ReportAutoHide.REVIEW.equals(p.getStat()))
                .isPresent();
    }

    static final java.util.Set<String> CMT_REACTIONS = java.util.Set.of("LIKE", "LOVE", "HAHA", "WOW", "SAD", "ANGRY");

    static final String STAT_SCHEDULED = "SCHEDULED";
    static final int RSRV_MIN_MINUTES = 5, RSRV_MAX_DAYS = 30;

    /** 예약 시각(ISO, 서버 시간대 기준 로컬 시각). 없으면 null(바로 게시). 5분 뒤~30일 안만 받는다. */
    private static java.time.LocalDateTime parseReservation(Object raw) {
        if (raw == null || raw.toString().isBlank()) return null;
        java.time.LocalDateTime t;
        try {
            String v = raw.toString();
            t = v.endsWith("Z") || v.matches(".*[+-]\\d{2}:\\d{2}$")
                    ? java.time.OffsetDateTime.parse(v).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    : java.time.LocalDateTime.parse(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("예약 시각을 읽지 못했어요.");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (t.isBefore(now.plusMinutes(RSRV_MIN_MINUTES - 1)))
            throw new IllegalArgumentException("예약은 " + RSRV_MIN_MINUTES + "분 뒤부터 걸 수 있어요.");
        if (t.isAfter(now.plusDays(RSRV_MAX_DAYS)))
            throw new IllegalArgumentException("예약은 " + RSRV_MAX_DAYS + "일 안으로만 걸 수 있어요.");
        return t.withSecond(0).withNano(0);
    }

    /** 새 글이 "세상에 나올 때" 한 번만 하는 일 — 바로 올릴 때와 예약 글이 올라갈 때 똑같이. */
    private void publishEffects(TblPost post, long userSn) {
        actionLogService.log(userSn, TblUserActionLog.TARGET_POST, post.getPostSn(), TblUserActionLog.ACTION_POST);
        eventPublisher.publishEvent(new PostCreatedEvent(post.getPostSn(), userSn, post.getCommSn(), post.getFrstCrtDt()));
        notifyCommunityMembers(post, userSn);
        mentionService.notify(post.getPostSn(), userSn, post.getPostCntnt(), null, List.of());
        keywordService.onPublished(post);   // 관심 키워드 알림
        badgeService.evaluate(userSn);   // 첫 글·글 10개·첫 투표 같은 뱃지
    }

    /**
     * 예약 글 하나를 올린다. 조건부 갱신(STAT='SCHEDULED' 일 때만)이라 서버가 여러 대여도 한 번만 올라간다.
     * 작성 시각을 지금으로 옮긴다 — 예약을 건 시각으로 두면 최신 피드에서 묻힌다.
     * @return 이번에 올렸으면 true
     */
    public boolean publishScheduled(long postSn) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        long n = new JPAQueryFactory(em).update(POST)
                .set(POST.stat, "ACTIVE").set(POST.frstCrtDt, now)
                .where(POST.postSn.eq(postSn), POST.stat.eq(STAT_SCHEDULED))
                .execute();
        if (n == 0) return false;
        em.flush();
        em.clear();
        TblPost post = tblPostRepository.findById(postSn).orElseThrow();
        publishEffects(post, post.getUserSn());
        return true;
    }

    /** 예약 글 관리 — 쓴 사람만. params: postSn, action(PUBLISH=지금 올리기 | RESCHEDULE), rsrvAt(RESCHEDULE 때) */
    public ResultVO updateReservation(Map<String, Object> params, long me) {
        ResultVO r = new ResultVO();
        try {
            long postSn = Long.parseLong(String.valueOf(params.get("postSn")));
            TblPost post = tblPostRepository.findById(postSn).orElse(null);
            if (post == null || post.getUserSn() != me || !"Y".equals(post.getActvtnYn()) || !STAT_SCHEDULED.equals(post.getStat())) {
                r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                r.setResultMessage("예약된 내 글이 아니에요. 이미 올라갔을 수 있어요.");
                return r;
            }
            if ("PUBLISH".equals(params.get("action"))) {
                publishScheduled(postSn);
            } else {
                post.setRsrvAt(parseReservation(params.get("rsrvAt")));
                post.setMdfrSn(me);
                tblPostRepository.save(post);
                r.putResult("rsrvAt", post.getRsrvAt());
            }
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        }
        return r;
    }

    /** 때가 된 예약 글 번호들. */
    @Transactional(readOnly = true)
    public List<Long> dueScheduled() {
        return new JPAQueryFactory(em).select(POST.postSn).from(POST)
                .where(POST.stat.eq(STAT_SCHEDULED), POST.actvtnYn.eq("Y"), POST.rsrvAt.loe(java.time.LocalDateTime.now()))
                .orderBy(POST.rsrvAt.asc()).limit(100).fetch();
    }

    /** 내 예약 글 — 내 프로필의 「예약된 글」. */
    @Transactional(readOnly = true)
    public ResultVO getMyScheduled(long me) {
        ResultVO r = new ResultVO();
        QTblComm comm = QTblComm.tblComm;
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Tuple t : new JPAQueryFactory(em).select(POST.postSn, POST.postTtl, POST.rsrvAt, comm.commNm, comm.commDsplNm)
                .from(POST).leftJoin(comm).on(comm.commSn.eq(POST.commSn))
                .where(POST.userSn.eq(me), POST.stat.eq(STAT_SCHEDULED), POST.actvtnYn.eq("Y"))
                .orderBy(POST.rsrvAt.asc()).fetch()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("postSn", t.get(POST.postSn));
            m.put("postTtl", t.get(POST.postTtl));
            m.put("rsrvAt", t.get(POST.rsrvAt));
            m.put("commNm", t.get(comm.commNm));
            m.put("commDsplNm", t.get(comm.commDsplNm));
            list.add(m);
        }
        r.putResult("list", list);
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /**
     * 커뮤니티 새 글 알림. 일반 글은 <b>알림을 켠 멤버</b>(notifYn='Y')에게, 공지는 구독과
     * 무관하게 <b>활성 멤버 전원</b>에게 간다. 글쓴이 본인은 리스너가 거른다.
     */
    private void notifyCommunityMembers(TblPost post, long authorSn) {
        if (post.getCommSn() == null) return;
        boolean notice = "NOTICE".equals(post.getPostTyp());
        QTblCommMbr mbr = QTblCommMbr.tblCommMbr;
        BooleanExpression cond = mbr.commSn.eq(post.getCommSn())
                .and(mbr.stat.eq("ACTIVE")).and(mbr.actvtnYn.eq("Y"))
                .and(mbr.userSn.ne(authorSn));
        if (!notice) cond = cond.and(mbr.notifYn.eq("Y"));
        List<Long> receivers = new JPAQueryFactory(em).select(mbr.userSn).from(mbr).where(cond).fetch();
        if (receivers.isEmpty()) return;
        QTblComm comm = QTblComm.tblComm;
        String commName = new JPAQueryFactory(em).select(comm.commDsplNm).from(comm)
                .where(comm.commSn.eq(post.getCommSn())).fetchOne();
        String typ = notice ? "COMM_NOTICE" : "COMM_POST";
        for (Long r : receivers) {
            eventPublisher.publishEvent(new NotifTriggerEvent(r, authorSn, typ, post.getPostSn(), "POST", commName));
        }
    }

    /** 공지(NOTICE)는 커뮤니티 OWNER/ADMIN 만 쓸 수 있다. 그 외는 조용히 NORMAL 로 내린다. */
    private String resolvePostTyp(Map<String, Object> params, Long commSn, long userSn, String fallback) {
        Object raw = params.get("postTyp");
        if (raw == null) return fallback;
        String typ = raw.toString();
        if ("NOTICE".equals(typ) && !isManagerRole(roleIn(commSn, userSn))) return "NORMAL";
        return "NOTICE".equals(typ) ? "NOTICE" : "NORMAL";
    }

    // ─────────────────────────────────────────────────────────────────────
    // 상세
    // ─────────────────────────────────────────────────────────────────────

    public ResultVO getPost(TblPost tblPost, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long postSn = tblPost.getPostSn();
            // 예약 글은 쓴 사람에게만 보인다(올라가기 전 확인·수정·취소용)
            // 신고가 몰려 가려진 글(REVIEW)도 쓴 사람은 연다 — 왜 안 보이는지 알아야 한다
            com.querydsl.core.types.dsl.BooleanExpression aliveOrMine = me == null ? PostQuery.alive()
                    : PostQuery.alive().or(POST.stat.in(STAT_SCHEDULED, com.knowra.safety.service.ReportAutoHide.REVIEW).and(POST.actvtnYn.eq("Y")).and(POST.userSn.eq(me)));
            Tuple t = postQuery.base()
                    .where(POST.postSn.eq(postSn), aliveOrMine, PostQuery.visibleTo(me))
                    .fetchOne();
            if (t == null) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("게시글을 찾을 수 없습니다.");
                return resultVO;
            }
            PostDTO dto = postQuery.decorate(List.of(t), me).get(0);
            resultVO.putResult("post", dto);
            tblPostRepository.findById(postSn).ifPresent(p -> {
                if (STAT_SCHEDULED.equals(p.getStat())) resultVO.putResult("rsrvAt", p.getRsrvAt());
                if (com.knowra.safety.service.ReportAutoHide.REVIEW.equals(p.getStat())) resultVO.putResult("underReview", true);
            });
            // 값을 돌려주지 않는 콜백은 줄마다 불리고 커서는 이미 그 줄에 있다 — rs.next() 를 부르면 하나뿐인 줄을 건너뛴다
            if (me != null) resultVO.putResult("muted", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM knowra_post.tbl_post_mute WHERE user_sn = ? AND post_sn = ?", Long.class, me, postSn) > 0);
            jdbc.query("SELECT COUNT(*), MAX(edited_at) FROM knowra_post.tbl_post_hist WHERE post_sn = ?", rs -> {
                if (rs.getLong(1) > 0) { resultVO.putResult("editCount", rs.getLong(1)); resultVO.putResult("editedAt", rs.getTimestamp(2).toLocalDateTime()); }
            }, postSn);
            resultVO.putResult("myLikeTyp", dto.getMyLikeTyp());
            resultVO.putResult("poll", pollService.view(postSn, dto.getUserSn(), me));
            if (dto.getCommSn() != null) {
                TblComm community = tblCommRepository.findByCommSn(dto.getCommSn());
                resultVO.putResult("comm", community);
                resultVO.putResult("myRole", roleIn(dto.getCommSn(), me));
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 글 수정 기록 — 글을 볼 수 있는 사람이면 누구나(글 자체가 공개된 내용이다). 최신 판이 먼저.
     * 고친 사람은 「작성자」 또는 운영진 이름 — 익명 커뮤니티는 「운영진」으로만(이름이 익명을 깬다).
     */
    public ResultVO getPostHistory(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long postSn = Long.parseLong(String.valueOf(params.get("postSn")));
            if (!canReadPost(postSn, me)) throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
            TblPost post = tblPostRepository.findByPostSn(postSn);
            boolean anonymous = post.getCommSn() != null && postQuery.isAnonymousComm(post.getCommSn());
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> r : jdbc.queryForList("""
                    SELECT h.hist_sn, h.post_ttl, h.post_cntnt, h.edited_by, h.edited_at, u.nick_name, u.name
                    FROM knowra_post.tbl_post_hist h LEFT JOIN knowra_user.tbl_user u ON u.user_sn = h.edited_by
                    WHERE h.post_sn = ? ORDER BY h.hist_sn DESC LIMIT 50""", postSn)) {
                Map<String, Object> m = new LinkedHashMap<>();
                long editor = ((Number) r.get("edited_by")).longValue();
                m.put("histSn", r.get("hist_sn"));
                m.put("title", r.get("post_ttl"));
                m.put("content", r.get("post_cntnt"));   // 저장 전에 HtmlSanitizer 를 거친 본문 그대로
                m.put("editedAt", r.get("edited_at"));
                m.put("editor", editor == post.getUserSn() ? "작성자"
                        : anonymous ? "운영진" : (r.get("name") != null && !String.valueOf(r.get("name")).isBlank() ? r.get("name") : r.get("nick_name")) + " (운영진)");
                list.add(m);
            }
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

    /** 이 글 알림 끄기/켜기 — params: postSn, mute(true|false) */
    public ResultVO setPostMute(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long postSn = Long.parseLong(String.valueOf(params.get("postSn")));
            boolean mute = Boolean.parseBoolean(String.valueOf(params.get("mute")));
            if (!canReadPost(postSn, me)) throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
            if (mute) jdbc.update("INSERT INTO knowra_post.tbl_post_mute (user_sn, post_sn) VALUES (?, ?) ON CONFLICT DO NOTHING", me, postSn);
            else jdbc.update("DELETE FROM knowra_post.tbl_post_mute WHERE user_sn = ? AND post_sn = ?", me, postSn);
            resultVO.putResult("muted", mute);
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

    public ResultVO viewPost(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long postSn = Long.parseLong(params.get("postSn").toString());
            redisApiService.incrementViewCount(REDIS_DB, "post", postSn);
            if (me != null) {
                actionLogService.log(me, TblUserActionLog.TARGET_POST, postSn, TblUserActionLog.ACTION_VIEW);
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 목록
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 커뮤니티 게시판 목록.
     * <ul>
     *   <li>POPULAR: likeCnt 내림차순, page 기반(50개)</li>
     *   <li>NOTICE: 공지만, postSn 커서</li>
     *   <li>그 외(LATEST): 첫 페이지는 공지 전부 + 일반 글, 이후 페이지는 일반 글만 postSn 커서</li>
     * </ul>
     */
    public ResultVO getCommPostList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long   commSn  = Long.parseLong(params.get("commSn").toString());
            String listTyp = params.get("listTyp") != null ? params.get("listTyp").toString() : "LATEST";
            Long   cursor  = params.get("cursor")  != null ? Long.parseLong(params.get("cursor").toString()) : null;
            int    page    = params.get("page")    != null ? Integer.parseInt(params.get("page").toString()) : 0;

            BooleanExpression condition = POST.commSn.eq(commSn).and(PostQuery.alive()).and(PostQuery.visibleTo(me)).and(PostQuery.notMuted(me));
            // 말머리로 걸러 보기 — 고정·공지도 같이 거른다
            if (params.get("flairSn") != null && !String.valueOf(params.get("flairSn")).isBlank())
                condition = condition.and(POST.flairSn.eq(Long.parseLong(String.valueOf(params.get("flairSn")))));
            List<Tuple> tuples;
            if ("POPULAR".equals(listTyp)) {
                tuples = postQuery.base()
                        .where(condition)
                        .orderBy(POST.likeCnt.desc(), POST.postSn.desc())
                        .offset((long) page * PAGE_SIZE)
                        .limit(PAGE_SIZE)
                        .fetch();
            } else if ("NOTICE".equals(listTyp)) {
                BooleanExpression c = condition.and(POST.postTyp.eq("NOTICE"));
                if (cursor != null) c = c.and(POST.postSn.lt(cursor));
                tuples = postQuery.base().where(c).orderBy(POST.postSn.desc()).limit(PAGE_SIZE).fetch();
            } else if (cursor == null) {
                // 첫 페이지: 운영진이 고정한 글 → 공지 → 일반 글. 고정된 글은 아래 목록에서 다시 나오지 않는다.
                List<Tuple> pinned = postQuery.base()
                        .where(condition.and(POST.pinnedAt.isNotNull()))
                        .orderBy(POST.pinnedAt.desc())
                        .fetch();
                List<Tuple> notices = postQuery.base()
                        .where(condition.and(POST.postTyp.eq("NOTICE")).and(POST.pinnedAt.isNull()))
                        .orderBy(POST.postSn.desc())
                        .fetch();
                int normalLimit = Math.max(PAGE_SIZE - notices.size() - pinned.size(), 0);
                List<Tuple> normals = postQuery.base()
                        .where(condition.and(POST.postTyp.eq("NORMAL")).and(POST.pinnedAt.isNull()))
                        .orderBy(POST.postSn.desc())
                        .limit(normalLimit)
                        .fetch();
                tuples = new ArrayList<>(pinned);
                tuples.addAll(notices);
                tuples.addAll(normals);
            } else {
                tuples = postQuery.base()
                        .where(condition.and(POST.postTyp.eq("NORMAL")).and(POST.pinnedAt.isNull()).and(POST.postSn.lt(cursor)))
                        .orderBy(POST.postSn.desc())
                        .limit(PAGE_SIZE)
                        .fetch();
            }

            List<PostDTO> list = postQuery.decorate(tuples, me);
            Long nextCursor = list.size() == PAGE_SIZE ? list.get(list.size() - 1).getPostSn() : null;
            resultVO.putResult("list", list);
            resultVO.putResult("nextCursor", nextCursor); // null이면 마지막 페이지
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 한 사용자가 쓴 글 (전체 공개 + viewer 가 볼 수 있는 커뮤니티 글), 작성일 커서, 최신순. */
    public List<PostDTO> fetchPostsByUser(long userSn, Long viewerSn, LocalDateTime cursor, int size) {
        BooleanExpression condition = POST.userSn.eq(userSn)
                .and(PostQuery.alive())
                .and(PostQuery.visibleTo(viewerSn));
        // 남의 프로필엔 익명 커뮤니티 글을 싣지 않는다 — 실으면 「이 사람이 쓴 익명 글」이 드러난다
        if (viewerSn == null || viewerSn != userSn) condition = condition.and(PostQuery.notAnonymous());
        if (cursor != null) condition = condition.and(POST.frstCrtDt.lt(cursor));
        List<Tuple> tuples = postQuery.base()
                .where(condition)
                .orderBy(POST.frstCrtDt.desc())
                .limit(size)
                .fetch();
        return postQuery.decorate(tuples, viewerSn);
    }

    /** 태그가 달린 글 (viewer 가 볼 수 있는 것만), 작성일 커서, 최신순. */
    public List<PostDTO> fetchPostsByTag(long tagSn, Long viewerSn, LocalDateTime cursor, int size) {
        QTblPostTag qTag = QTblPostTag.tblPostTag;
        BooleanExpression condition = POST.postSn.in(
                        JPAExpressions.select(qTag.postSn).from(qTag).where(qTag.tagSn.eq(tagSn)))
                .and(PostQuery.alive())
                .and(PostQuery.visibleTo(viewerSn)).and(PostQuery.notMuted(viewerSn));
        if (cursor != null) condition = condition.and(POST.frstCrtDt.lt(cursor));
        List<Tuple> tuples = postQuery.base()
                .where(condition)
                .orderBy(POST.frstCrtDt.desc())
                .limit(size)
                .fetch();
        return postQuery.decorate(tuples, viewerSn);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 좋아요 · 댓글 · 반응
    // ─────────────────────────────────────────────────────────────────────

    public ResultVO setPostLike(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long   userSn   = me;
            long   postSn   = Long.parseLong(params.get("postSn").toString());
            String postLike = params.get("postLike").toString().toUpperCase();
            // 좋아요 하나뿐이다. 화면은 'up' 만 보내지만 API 로 'DOWN' 을 넣으면 옛 비추천 경로가
            // 살아 있어 수가 음수로 내려갔다(저장됨 화면의 -1 이 그 흔적). 여기서 막는다.
            if (!canSeePost(postSn, userSn)) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("볼 수 없는 글이에요.");
                return resultVO;
            }
            if (!"UP".equals(postLike)) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("지원하지 않는 반응입니다.");
                return resultVO;
            }
            boolean up      = true;

            TblPostLike like = tblPostLikeRepository.findByPostSnAndUserSn(postSn, userSn);
            int delta;
            if (like == null) {
                // 처음 반응 → 추가
                delta = up ? 1 : -1;
                tblPostLikeRepository.save(TblPostLike.builder()
                        .userSn(userSn).postSn(postSn).likeTyp(postLike).creatrSn(userSn).build());
                actionLogService.log(userSn, TblUserActionLog.TARGET_POST, postSn, TblUserActionLog.ACTION_LIKE);
                if (up) {
                    interestScoreService.update(userSn, TblUserActionLog.TARGET_POST, postSn, 3);
                    interestScoreService.updateForTags(userSn, tagService.fetchTagSns(postSn), 3);
                }
            } else if (like.getLikeTyp().equals(postLike)) {
                // 같은 반응 재클릭 → 취소
                delta = up ? -1 : 1;
                tblPostLikeRepository.delete(like);
                if (up) {
                    interestScoreService.update(userSn, TblUserActionLog.TARGET_POST, postSn, -3);
                    interestScoreService.updateForTags(userSn, tagService.fetchTagSns(postSn), -3);
                }
            } else {
                // 옛 DOWN 행 → UP 전환. DOWN 은 V16 에서 지웠고 like_cnt 는 UP 행 수라 +1 이다
                // (예전 +2 는 DOWN 이 수를 깎던 시절 계산).
                delta = 1;
                like.setLikeTyp(postLike);
                tblPostLikeRepository.save(like);
                double tagDelta = up ? 3 : -3;
                interestScoreService.update(userSn, TblUserActionLog.TARGET_POST, postSn, tagDelta);
                interestScoreService.updateForTags(userSn, tagService.fetchTagSns(postSn), tagDelta);
            }

            new JPAQueryFactory(em).update(POST)
                    .set(POST.likeCnt, POST.likeCnt.add(delta))
                    .where(POST.postSn.eq(postSn))
                    .execute();
            eventPublisher.publishEvent(new PostReactedEvent(postSn, "LIKE", delta));

            // 좋아요 시에만 알림 (취소·DOWN은 제외)
            if (up && delta > 0) {
                Long ownerSn = ownerOf(postSn);
                if (ownerSn != null) {
                    eventPublisher.publishEvent(new NotifTriggerEvent(ownerSn, userSn, "LIKE", postSn, "POST"));
                }
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO setPostCmt(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn    = me;
            long postSn    = Long.parseLong(params.get("postSn").toString());
            Long prntCmtSn = params.get("prntCmtSn") != null
                    ? Long.parseLong(params.get("prntCmtSn").toString()) : null;
            // 볼 수 없는 글(비공개 커뮤니티·차단·숨김·삭제)에는 댓글도 못 단다 — 예전엔 postSn 만 알면 달 수 있었다
            if (postQuery.base().where(POST.postSn.eq(postSn), PostQuery.alive(), PostQuery.visibleTo(userSn)).fetchFirst() == null) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("댓글을 달 수 없는 글이에요.");
                return resultVO;
            }
            // 답글의 부모는 같은 글의 살아 있는 댓글이어야 한다 — 예전엔 아무 댓글 번호(비공개 글 것도)나 넣어
            // 그 댓글 주인에게 「답글」 알림을 보낼 수 있었다
            if (prntCmtSn != null && tblPostCmtRepository.findById(prntCmtSn)
                    .filter(c -> c.getPostSn() == postSn && "Y".equals(c.getActvtnYn())).isEmpty()) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("답글을 달 수 없는 댓글이에요.");
                return resultVO;
            }
            String cmtText = params.get("cmtCntnt") == null ? "" : params.get("cmtCntnt").toString().trim();
            String imgUrl  = cleanImgUrl(params.get("imgUrl"));
            if (cmtText.isEmpty() && imgUrl == null) throw new IllegalArgumentException("내용이나 사진을 넣어 주세요.");

            tblPostCmtRepository.save(TblPostCmt.builder()
                    .postSn(postSn)
                    .userSn(userSn)
                    .prntCmtSn(prntCmtSn)
                    .cmtCntnt(cmtText)
                    .imgUrl(imgUrl)
                    .creatrSn(userSn)
                    .build());
            actionLogService.log(userSn, TblUserActionLog.TARGET_POST, postSn, TblUserActionLog.ACTION_COMMENT);
            interestScoreService.updateForTags(userSn, tagService.fetchTagSns(postSn), 4);

            new JPAQueryFactory(em).update(POST)
                    .set(POST.cmtCnt, POST.cmtCnt.add(1))
                    .where(POST.postSn.eq(postSn))
                    .execute();
            eventPublisher.publishEvent(new PostReactedEvent(postSn, "COMMENT", 1));

            // 답글이면 부모 댓글 작성자에게 REPLY, 글 주인에게 COMMENT. 둘이 같은 사람이면 REPLY 하나만.
            Long ownerSn  = ownerOf(postSn);
            Long parentSn = null;
            if (prntCmtSn != null) {
                parentSn = tblPostCmtRepository.findById(prntCmtSn).map(TblPostCmt::getUserSn).orElse(null);
                if (parentSn != null) {
                    eventPublisher.publishEvent(new NotifTriggerEvent(parentSn, userSn, "REPLY", postSn, "POST"));
                }
            }
            if (ownerSn != null && !ownerSn.equals(parentSn)) {
                eventPublisher.publishEvent(new NotifTriggerEvent(ownerSn, userSn, "COMMENT", postSn, "POST"));
            }
            // @멘션 — 이미 COMMENT/REPLY 를 받은 사람은 빼서 한 동작에 알림 하나만
            List<Long> notified = new java.util.ArrayList<>();
            if (ownerSn != null) notified.add(ownerSn);
            if (parentSn != null) notified.add(parentSn);
            mentionService.notify(postSn, userSn, cmtText, null, notified);
            badgeService.evaluate(userSn);   // 첫 댓글·수다쟁이
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

    /** 댓글 수정 — 작성자 본인만. params: postCmtSn, cmtCntnt */
    public ResultVO setPostCmtEdit(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn    = me;
            long postCmtSn = Long.parseLong(params.get("postCmtSn").toString());
            String cntnt   = params.get("cmtCntnt") == null ? "" : params.get("cmtCntnt").toString().trim();

            TblPostCmt cmt = tblPostCmtRepository.findById(postCmtSn)
                    .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
            if (cmt.getUserSn() != userSn || !"ACTIVE".equals(cmt.getStat()) || !"Y".equals(cmt.getActvtnYn())) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("수정 권한이 없습니다.");
                return resultVO;
            }
            String beforeText = cmt.getCmtCntnt();
            // imgUrl 키가 오면 바꾼다(null·빈 값이면 사진 빼기). 안 오면 그대로 둔다.
            // ⚠️ 검사를 먼저 끝내고 나서 엔티티를 고친다 — 이 클래스는 트랜잭션 안이라, 고친 뒤에 예외를 잡아
            // 실패로 답해도 바뀐 값이 그대로 커밋됐다(거절된 수정이 사진을 지워 버렸다).
            String nextImg = params.containsKey("imgUrl") ? cleanImgUrl(params.get("imgUrl")) : cmt.getImgUrl();
            if (cntnt.isEmpty() && nextImg == null) throw new IllegalArgumentException("내용이 비어 있습니다.");
            cmt.setImgUrl(nextImg);
            cmt.setCmtCntnt(cntnt);
            cmt.setMdfrSn(userSn);
            tblPostCmtRepository.save(cmt);
            mentionService.notify(cmt.getPostSn(), userSn, cntnt, beforeText, List.of());   // 새로 넣은 멘션만
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

    /**
     * 댓글 삭제 — 작성자 본인 또는 글을 관리할 수 있는 사람(글 주인·커뮤니티 운영진).
     * 답글이 달린 댓글은 행을 숨기지 않고 stat=DELETED 로 남긴다 — 답글이 고아가 되면 안 된다.
     * 답글이 없으면 그냥 비활성화한다. 어느 쪽이든 글의 댓글 수는 1 줄어든다.
     */
    public ResultVO setPostCmtDel(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn    = me;
            long postCmtSn = Long.parseLong(params.get("postCmtSn").toString());
            TblPostCmt cmt = tblPostCmtRepository.findById(postCmtSn)
                    .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
            if (!"ACTIVE".equals(cmt.getStat()) || !"Y".equals(cmt.getActvtnYn())) {
                resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
                resultVO.setResultMessage("이미 삭제된 댓글입니다.");
                return resultVO;
            }
            TblPost post = tblPostRepository.findByPostSn(cmt.getPostSn());
            boolean allowed = cmt.getUserSn() == userSn || (post != null && canManage(post, userSn));
            if (!allowed) {
                resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
                resultVO.setResultMessage("삭제 권한이 없습니다.");
                return resultVO;
            }

            deleteCmtInternal(cmt, userSn);
            if (cmt.getUserSn() != userSn && post != null && post.getCommSn() != null)
                modLog.record(post.getCommSn(), userSn, com.knowra.community.modlog.ModLogService.CMT_DELETE, cmt.getUserSn(), post.getPostSn(),
                        org.jsoup.Jsoup.parse(cmt.getCmtCntnt() == null ? "" : cmt.getCmtCntnt()).text());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.DELETE_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO setPostCmtReact(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long   userSn    = me;
            long   postCmtSn = Long.parseLong(params.get("postCmtSn").toString());
            String reactTyp  = params.get("reactTyp").toString();
            TblPostCmt target = tblPostCmtRepository.findById(postCmtSn).orElse(null);
            if (!CMT_REACTIONS.contains(reactTyp) || target == null || !"Y".equals(target.getActvtnYn())
                    || !canSeePost(target.getPostSn(), userSn)) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("반응할 수 없는 댓글이에요.");
                return resultVO;
            }

            TblPostCmtReact existing = tblPostCmtReactRepository.findByPostCmtSnAndUserSn(postCmtSn, userSn);
            if (existing == null) {
                tblPostCmtReactRepository.save(TblPostCmtReact.builder()
                        .postCmtSn(postCmtSn).userSn(userSn).reactTyp(reactTyp).creatrSn(userSn).build());
            } else if (existing.getReactTyp().equals(reactTyp)) {
                tblPostCmtReactRepository.delete(existing);        // 같은 반응 재클릭 → 취소
            } else {
                existing.setReactTyp(reactTyp);                     // 다른 반응으로 전환
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    public static final int MAX_PINNED = 3;

    /** 커뮤니티 상단 고정/해제 — 그 커뮤니티 운영진(OWNER/ADMIN)만. params: postSn, pin */
    public ResultVO setPostPin(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long postSn = Long.parseLong(String.valueOf(params.get("postSn")));
            boolean pin = Boolean.parseBoolean(String.valueOf(params.get("pin")));
            TblPost post = tblPostRepository.findByPostSn(postSn);
            if (post == null || !"Y".equals(post.getActvtnYn())) throw new IllegalArgumentException("글을 찾을 수 없어요.");
            if (post.getCommSn() == null) throw new IllegalArgumentException("커뮤니티 글만 고정할 수 있어요.");
            if (!isManagerRole(roleIn(post.getCommSn(), me))) throw new IllegalArgumentException("커뮤니티 운영진만 고정할 수 있어요.");
            boolean was = post.getPinnedAt() != null;
            if (pin && post.getPinnedAt() == null) {
                long n = new JPAQueryFactory(em).select(POST.count()).from(POST)
                        .where(POST.commSn.eq(post.getCommSn()), POST.pinnedAt.isNotNull(), POST.actvtnYn.eq("Y")).fetchOne();
                if (n >= MAX_PINNED) throw new IllegalArgumentException("고정은 " + MAX_PINNED + "개까지예요. 다른 글을 먼저 풀어 주세요.");
                post.setPinnedAt(java.time.LocalDateTime.now());
            } else if (!pin) {
                post.setPinnedAt(null);
            }
            tblPostRepository.save(post);
            if (was != (post.getPinnedAt() != null))
                modLog.record(post.getCommSn(), me, pin ? com.knowra.community.modlog.ModLogService.POST_PIN : com.knowra.community.modlog.ModLogService.POST_UNPIN, post.getUserSn(), postSn, post.getPostTtl());
            resultVO.putResult("pinned", post.getPinnedAt() != null);
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

    /**
     * 댓글 사진 경로 — 이 서버가 올린 사진만 받는다(저장소 공개 URL, 또는 예전 /upload/editor/...).
     * 아무 URL 이나 받으면 남의 서버 이미지를 끌어와 열람자 IP 를 모으는 데 쓸 수 있다.
     */
    public static String cleanImgUrl(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        // 예전 모양(/upload/editor/...) 또는 우리 저장소가 만든 공개 이미지(public/{prefix}/editor/...)만
        boolean legacy = s.matches("^/upload/editor/\\d{4}/\\d{2}/\\d{2}/\\d+_\\d+\\.(png|jpe?g|gif|webp)$");
        String key = legacy ? null : com.knowra.cmm.storage.StorageUrls.keyFromUrl(s);
        // 키 모양: public/{prefix}/{소유자}/editor/{uuid}.{ext} (FileUtil — 전역 규칙 키 구조)
        boolean ours = key != null && key.matches("^public/[A-Za-z0-9_-]+/\\d+/editor/[0-9a-f]{32}\\.(png|jpe?g|gif|webp)$");
        if (!legacy && !ours) throw new IllegalArgumentException("사진 경로가 올바르지 않아요.");
        return s;
    }

    private Long ownerOf(long postSn) {
        return new JPAQueryFactory(em)
                .select(POST.userSn).from(POST)
                .where(POST.postSn.eq(postSn)).fetchOne();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 삭제
    // ─────────────────────────────────────────────────────────────────────

    public ResultVO setPostDel(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            long postSn = Long.parseLong(params.get("postSn").toString());
            TblPost tblPost = tblPostRepository.findByPostSn(postSn);
            if (tblPost == null || !canManage(tblPost, userSn)) {
                resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
                resultVO.setResultMessage("삭제 권한이 없습니다.");
                return resultVO;
            }
            deletePostInternal(tblPost, userSn);
            if (tblPost.getUserSn() != userSn && tblPost.getCommSn() != null)
                modLog.record(tblPost.getCommSn(), userSn, com.knowra.community.modlog.ModLogService.POST_DELETE, tblPost.getUserSn(), postSn, tblPost.getPostTtl());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.DELETE_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 글 삭제 본문 — 작성자/커뮤니티 관리자(setPostDel)와 운영 관리자(adminDeletePost)가 같이 쓴다. */
    private void deletePostInternal(TblPost tblPost, long by) {
        tblPost.setActvtnYn("N");
        tblPost.setMdfrSn(by);
        tblPostRepository.save(tblPost);
        eventPublisher.publishEvent(new PostDeletedEvent(tblPost.getPostSn()));
    }

    /** 댓글 삭제 본문. 답글이 살아 있으면 자리는 남긴다(stat=DELETED, actvtnYn 유지). */
    private void deleteCmtInternal(TblPostCmt cmt, long by) {
        QTblPostCmt c = QTblPostCmt.tblPostCmt;
        long liveReplies = new JPAQueryFactory(em).select(c.count()).from(c)
                .where(c.prntCmtSn.eq(cmt.getPostCmtSn()).and(c.stat.eq("ACTIVE")).and(c.actvtnYn.eq("Y")))
                .fetchOne();
        cmt.setStat("DELETED");
        if (liveReplies == 0) cmt.setActvtnYn("N");
        cmt.setMdfrSn(by);
        tblPostCmtRepository.save(cmt);
        new JPAQueryFactory(em).update(POST)
                .set(POST.cmtCnt, POST.cmtCnt.subtract(1))
                .where(POST.postSn.eq(cmt.getPostSn()).and(POST.cmtCnt.gt(0)))
                .execute();
        eventPublisher.publishEvent(new PostReactedEvent(cmt.getPostSn(), "COMMENT", -1));
    }

    /**
     * 운영 관리자 삭제 — 권한 검사는 호출부(/api/admin/** 는 ROLE_ADMIN 만 통과)가 이미 했다.
     * 작성자·커뮤니티 권한과 무관하게 지운다. 이미 지워진 글이면 false.
     */
    @Transactional
    public boolean adminDeletePost(long postSn, long adminSn) {
        TblPost tblPost = tblPostRepository.findByPostSn(postSn);
        if (tblPost == null || !"Y".equals(tblPost.getActvtnYn())) return false;
        deletePostInternal(tblPost, adminSn);
        return true;
    }

    @Transactional
    public boolean adminDeleteCmt(long postCmtSn, long adminSn) {
        TblPostCmt cmt = tblPostCmtRepository.findById(postCmtSn).orElse(null);
        if (cmt == null || !"ACTIVE".equals(cmt.getStat())) return false;
        deleteCmtInternal(cmt, adminSn);
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 댓글 목록 (루트 댓글 커서 + 대댓글 배치)
    // ─────────────────────────────────────────────────────────────────────

    public ResultVO getPostCmtList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            Long userSn = me;
            long postSn = Long.parseLong(params.get("postSn").toString());
            int  size   = params.get("size")   != null ? Integer.parseInt(params.get("size").toString()) : 10;
            Long cursor = params.get("cursor") != null ? Long.parseLong(params.get("cursor").toString()) : null;

            if (!canReadPost(postSn, userSn)) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("게시글을 찾을 수 없습니다.");
                return resultVO;
            }
            JPAQueryFactory q = new JPAQueryFactory(em);
            QTblPostCmt cmt        = QTblPostCmt.tblPostCmt;
            QTblUser    cmtUser    = new QTblUser("cmtUser");
            QTblComFile cmtPfpFile = new QTblComFile("cmtPfpFile");

            // ── 1. 루트 댓글만 커서 페이지네이션 (postCmtSn ASC) ──────────────
            // stat 으로 거르지 않는다 — 답글이 달린 채 지워진 댓글(DELETED, actvtnYn=Y)은 자리가 남아야
            // 답글이 보인다. 답글도 없이 지워진 댓글은 actvtnYn=N 이라 여기서 빠진다.
            BooleanExpression rootCondition = cmt.postSn.eq(postSn)
                    .and(cmt.actvtnYn.eq("Y"))
                    .and(cmt.prntCmtSn.isNull());
            // 차단한·차단당한 사람의 댓글은 뺀다(양방향). 글 목록(PostQuery.visibleTo)과 같은 규칙.
            BooleanExpression notBlocked = null;
            if (userSn != null) {
                com.knowra.safety.entity.QTblUserBlock blk = com.knowra.safety.entity.QTblUserBlock.tblUserBlock;
                notBlocked = cmt.userSn.notIn(JPAExpressions.select(blk.blockedSn).from(blk).where(blk.userSn.eq(userSn)))
                        .and(cmt.userSn.notIn(JPAExpressions.select(blk.userSn).from(blk).where(blk.blockedSn.eq(userSn))));
                rootCondition = rootCondition.and(notBlocked);
            }
            // 정렬: old(등록순, 기본) · new(최신순) — postCmtSn 커서 / top(인기순) — 반응+답글 점수라 단조롭지 않아
            // 커서 대신 오프셋을 쓴다. 한 글의 루트 댓글은 많아야 수백이라 점수는 메모리에서 매긴다.
            String sort = params.get("sort") == null ? "old" : params.get("sort").toString();
            Long nextCursorOverride = null;
            List<Tuple> rootTuples;
            com.querydsl.core.types.Expression<?>[] cols = { cmt, cmtUser.nickName, cmtUser.name,
                    cmtPfpFile.atchFilePathNm, cmtPfpFile.strgFileNm, cmtPfpFile.atchFileExtnNm, cmtPfpFile.fileKey };
            if ("top".equals(sort)) {
                List<Long> allRoots = q.select(cmt.postCmtSn).from(cmt).where(rootCondition).orderBy(cmt.postCmtSn.asc()).fetch();
                Map<Long, Long> score = new HashMap<>();
                tblPostCmtReactRepository.countByCmtSns(allRoots).forEach(row ->
                        score.merge(((Number) row[0]).longValue(), ((Number) row[2]).longValue(), Long::sum));
                q.select(cmt.prntCmtSn, cmt.count()).from(cmt)
                        .where(cmt.prntCmtSn.in(allRoots), cmt.actvtnYn.eq("Y")).groupBy(cmt.prntCmtSn).fetch()
                        .forEach(t -> score.merge(t.get(cmt.prntCmtSn), 2 * t.get(cmt.count()), Long::sum));
                List<Long> ordered = new ArrayList<>(allRoots);
                ordered.sort(Comparator.comparingLong((Long sn) -> score.getOrDefault(sn, 0L)).reversed().thenComparingLong(sn -> sn));
                int from = cursor == null ? 0 : (int) Math.max(0, cursor);
                List<Long> pageSns = from >= ordered.size() ? List.of() : ordered.subList(from, Math.min(ordered.size(), from + size));
                Map<Long, Tuple> byId = new HashMap<>();
                if (!pageSns.isEmpty()) {
                    q.select(cols).from(cmt).join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn)).leftJoin(cmtUser.pfp, cmtPfpFile)
                            .where(cmt.postCmtSn.in(pageSns)).fetch().forEach(t -> byId.put(t.get(cmt).getPostCmtSn(), t));
                }
                rootTuples = pageSns.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
                nextCursorOverride = from + size < ordered.size() ? (long) (from + size) : -1L;
            } else {
                boolean desc = "new".equals(sort);
                if (cursor != null) rootCondition = rootCondition.and(desc ? cmt.postCmtSn.lt(cursor) : cmt.postCmtSn.gt(cursor));
                rootTuples = q.select(cols)
                        .from(cmt)
                        .join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn))
                        .leftJoin(cmtUser.pfp, cmtPfpFile)
                        .where(rootCondition)
                        .orderBy(desc ? cmt.postCmtSn.desc() : cmt.postCmtSn.asc())
                        .limit(size)
                        .fetch();
            }

            if (rootTuples.isEmpty()) {
                resultVO.putResult("list", List.of());
                resultVO.putResult("nextCursor", null);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }
            List<Long> rootSns = rootTuples.stream().map(t -> t.get(cmt).getPostCmtSn()).toList();

            // ── 2. 대댓글 배치 로드 ────────────────────────────────────────────
            List<Tuple> replyTuples = q.select(cmt, cmtUser.nickName, cmtUser.name,
                            cmtPfpFile.atchFilePathNm, cmtPfpFile.strgFileNm, cmtPfpFile.atchFileExtnNm, cmtPfpFile.fileKey)
                    .from(cmt)
                    .join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn))
                    .leftJoin(cmtUser.pfp, cmtPfpFile)
                    .where(notBlocked == null
                            ? cmt.prntCmtSn.in(rootSns).and(cmt.actvtnYn.eq("Y"))
                            : cmt.prntCmtSn.in(rootSns).and(cmt.actvtnYn.eq("Y")).and(notBlocked))
                    .orderBy(cmt.postCmtSn.asc())
                    .fetch();

            // ── 3. 반응 수 / 내 반응 배치 조회 ───────────────────────────────
            List<Long> allSns = new ArrayList<>(rootSns);
            replyTuples.stream().map(t -> t.get(cmt).getPostCmtSn()).forEach(allSns::add);

            Map<Long, Map<String, Long>> reactionsMap = new HashMap<>();
            tblPostCmtReactRepository.countByCmtSns(allSns).forEach(row -> {
                long   cmtSn = ((Number) row[0]).longValue();
                String typ   = (String) row[1];
                long   cnt   = ((Number) row[2]).longValue();
                reactionsMap.computeIfAbsent(cmtSn, k -> new HashMap<>()).put(typ, cnt);
            });
            Map<Long, String> myReactMap = new HashMap<>();
            if (userSn != null) {
                tblPostCmtReactRepository.findByPostCmtSnInAndUserSn(allSns, userSn)
                        .forEach(r -> myReactMap.put(r.getPostCmtSn(), r.getReactTyp()));
            }

            // ── 4. 트리 조립 ──────────────────────────────────────────────────
            Function<Tuple, CmtDTO> toDto = t -> {
                TblPostCmt c      = t.get(cmt);
                long       sn     = c.getPostCmtSn();
                String     pathNm = t.get(cmtPfpFile.atchFilePathNm);
                String     pfpUrl = com.knowra.cmm.storage.StorageUrls.of(pathNm, t.get(cmtPfpFile.strgFileNm), t.get(cmtPfpFile.atchFileExtnNm), t.get(cmtPfpFile.fileKey));
                boolean mine = userSn != null && c.getUserSn() == userSn;
                boolean review = com.knowra.safety.service.ReportAutoHide.REVIEW.equals(c.getStat());
                // 지워진 댓글, 신고가 몰려 가려진 댓글(쓴 사람 말고)은 본문을 내보내지 않는다
                boolean hideBody = review ? !mine : !"ACTIVE".equals(c.getStat());
                return new CmtDTO(
                        sn, c.getUserSn(), t.get(cmtUser.nickName), t.get(cmtUser.name),
                        hideBody ? "" : c.getCmtCntnt(),
                        c.getLikeCnt(), c.getFrstCrtDt(), new ArrayList<>(),
                        reactionsMap.getOrDefault(sn, new HashMap<>()),
                        myReactMap.get(sn), pfpUrl,
                        review ? "REVIEW" : "ACTIVE".equals(c.getStat()) ? "ACTIVE" : "DELETED", c.getMdfcnDt(),
                        hideBody ? null : c.getImgUrl(), false, mine, false);
            };
            Map<Long, CmtDTO> rootMap = new LinkedHashMap<>();
            rootTuples.forEach(t -> rootMap.put(t.get(cmt).getPostCmtSn(), toDto.apply(t)));
            replyTuples.forEach(t -> {
                CmtDTO parent = rootMap.get(t.get(cmt).getPrntCmtSn());
                if (parent != null) parent.getReplies().add(toDto.apply(t));
            });
            List<CmtDTO> list = new ArrayList<>(rootMap.values());

            // ── 익명 커뮤니티: 글쓴이는 「글쓴이」, 나머지는 이 글에 처음 댓글을 단 순서대로 「익명1·익명2…」.
            //    같은 사람은 같은 번호라 대화가 이어져 보이고, 누구인지는 알 수 없다(번호·닉네임·사진 비움).
            TblPost thePost = tblPostRepository.findById(postSn).orElse(null);
            if (thePost != null && postQuery.isAnonymousComm(thePost.getCommSn())) {
                List<Long> order = q.select(cmt.userSn).from(cmt).where(cmt.postSn.eq(postSn))
                        .groupBy(cmt.userSn).orderBy(cmt.postCmtSn.min().asc()).fetch();
                Map<Long, String> alias = new HashMap<>();
                int n = 0;
                for (Long u : order) alias.put(u, u == thePost.getUserSn() ? "글쓴이" : PostQuery.ANONYMOUS_NAME + (++n));
                java.util.function.Consumer<CmtDTO> anon = d -> {
                    d.setAuthorNm(alias.getOrDefault(d.getUserSn(), PostQuery.ANONYMOUS_NAME));
                    d.setAnonymous(true); d.setUserSn(0); d.setNickName(null); d.setPfpUrl(null);
                };
                for (CmtDTO d : list) { anon.accept(d); d.getReplies().forEach(anon); }
            }

            // 내가 숨긴 사람의 댓글·답글은 접어 두라고 표시한다(차단처럼 빼지 않는다 — 답글 흐름이 끊긴다)
            if (userSn != null) {
                java.util.Set<Long> mutedSet = new java.util.HashSet<>(jdbc.queryForList("SELECT muted_sn FROM knowra_user.tbl_user_mute WHERE user_sn = ?", Long.class, userSn));
                if (!mutedSet.isEmpty()) for (CmtDTO d : list) {
                    d.setMuted(mutedSet.contains(d.getUserSn()));
                    if (d.getReplies() != null) d.getReplies().forEach(rp -> rp.setMuted(mutedSet.contains(rp.getUserSn())));
                }
            }

            // ── 5. nextCursor: 마지막 루트 댓글 SN (size 미달이면 null) ────────
            Long nextCursor = nextCursorOverride != null
                    ? (nextCursorOverride < 0 ? null : nextCursorOverride)
                    : (list.size() == size ? rootSns.get(rootSns.size() - 1) : null);
            resultVO.putResult("list", list);
            resultVO.putResult("nextCursor", nextCursor);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 저장(북마크)
    // ─────────────────────────────────────────────────────────────────────

    /** params: postSn, isSave. 같은 글을 두 번 저장해도 한 행만 남는다. */
    public ResultVO setPostSave(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long    userSn = me;
            long    postSn = Long.parseLong(params.get("postSn").toString());
            boolean isSave = (boolean) params.get("isSave");

            // folderSn: 저장하면서 폴더에 넣거나, 이미 저장한 글을 다른 폴더로 옮긴다. 남의 폴더면 무시하고 폴더 없이.
            Long folderSn = params.get("folderSn") == null ? null : Long.valueOf(params.get("folderSn").toString());
            if (folderSn != null && saveFolderRepository.findById(folderSn).filter(f -> f.getUserSn() == userSn).isEmpty()) folderSn = null;

            TblPostSave existing = tblPostSaveRepository.findByUserSnAndPostSn(userSn, postSn);
            if (isSave && !canSeePost(postSn, userSn)) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("볼 수 없는 글이에요.");
                return resultVO;
            }
            if (isSave) {
                if (existing != null) {
                    if (params.containsKey("folderSn")) { existing.setFolderSn(folderSn); tblPostSaveRepository.save(existing); }
                } else {
                    TblPostSave save = new TblPostSave();
                    save.setUserSn(userSn);
                    save.setPostSn(postSn);
                    save.setCreatrSn(userSn);
                    save.setFolderSn(folderSn);
                    tblPostSaveRepository.save(save);
                    actionLogService.log(userSn, TblUserActionLog.TARGET_POST, postSn, TblUserActionLog.ACTION_SCRAP);
                    interestScoreService.updateForTags(userSn, tagService.fetchTagSns(postSn), 5);
                }
            } else if (existing != null) {
                tblPostSaveRepository.delete(existing);
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 내가 저장한 글, 최근 저장 순. 커서는 저장 행의 SN(postSaveSn) — 클라이언트는 그대로 되돌려 준다.
     *
     * <p>예전 구현은 50개를 가정한 nextCursor 만 있고 실제 limit 이 없어 전부 내려보냈다.
     */
    public ResultVO getPostSaveList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            int  size   = params.get("size")   != null ? Integer.parseInt(params.get("size").toString()) : 20;
            Long cursor = params.get("cursor") != null ? Long.parseLong(params.get("cursor").toString()) : null;

            QTblPostSave qSave = QTblPostSave.tblPostSave;
            BooleanExpression condition = qSave.userSn.eq(userSn).and(qSave.actvtnYn.eq("Y"));
            // folderSn: 숫자면 그 폴더만, "none" 이면 폴더 없는 것만, 없으면 전체
            Object f = params.get("folderSn");
            if (f != null && !String.valueOf(f).isBlank()) {
                condition = "none".equals(String.valueOf(f)) ? condition.and(qSave.folderSn.isNull())
                        : condition.and(qSave.folderSn.eq(Long.valueOf(String.valueOf(f))));
            }
            if (cursor != null) condition = condition.and(qSave.postSaveSn.lt(cursor));
            List<Tuple> saves = new JPAQueryFactory(em)
                    .select(qSave.postSaveSn, qSave.postSn, qSave.folderSn)
                    .from(qSave)
                    .where(condition)
                    .orderBy(qSave.postSaveSn.desc())
                    .limit(size)
                    .fetch();

            List<Long> postSns = saves.stream().map(t -> t.get(qSave.postSn)).toList();
            Map<Long, Tuple> byPostSn = new HashMap<>();
            if (!postSns.isEmpty()) {
                postQuery.base()
                        .where(POST.postSn.in(postSns), PostQuery.alive(), PostQuery.visibleTo(me))
                        .fetch()
                        .forEach(t -> byPostSn.put(t.get(POST).getPostSn(), t));
            }
            // 저장 순서를 유지한다 — 글이 지워졌으면 건너뛴다
            List<Tuple> ordered = postSns.stream()
                    .map(byPostSn::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            List<PostDTO> list = postQuery.decorate(ordered, userSn);

            Long nextCursor = saves.size() == size ? saves.get(saves.size() - 1).get(qSave.postSaveSn) : null;
            Map<Long, Long> folderOf = new HashMap<>();
            saves.forEach(t -> { if (t.get(qSave.folderSn) != null) folderOf.put(t.get(qSave.postSn), t.get(qSave.folderSn)); });
            resultVO.putResult("list", list);
            resultVO.putResult("folderOf", folderOf);   // postSn → folderSn (화면의 「폴더」 표시·옮기기용)
            resultVO.putResult("nextCursor", nextCursor);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 최근 본 글 — 지난 30일 동안 연 글을 마지막으로 본 순서대로, 최대 50개.
     *
     * <p>근거는 행동 로그의 POST VIEW 다(따로 테이블을 두지 않는다). 지워졌거나 이제 볼 수 없는 글은 건너뛴다 —
     * 비공개 커뮤니티에서 나왔으면 제목도 보여 주면 안 된다.
     */
    public ResultVO getRecentViewList(Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            List<Long> postSns = jdbc.queryForList("""
                    SELECT target_sn FROM knowra_user.tbl_user_action_log
                    WHERE user_sn = ? AND target_type = ? AND action_type = ? AND reg_dt >= ?
                    GROUP BY target_sn ORDER BY MAX(reg_dt) DESC LIMIT 50""", Long.class,
                    me, TblUserActionLog.TARGET_POST, TblUserActionLog.ACTION_VIEW, LocalDateTime.now().minusDays(30));
            Map<Long, Tuple> byPostSn = new HashMap<>();
            if (!postSns.isEmpty()) {
                postQuery.base()
                        .where(POST.postSn.in(postSns), PostQuery.alive(), PostQuery.visibleTo(me))
                        .fetch()
                        .forEach(t -> byPostSn.put(t.get(POST).getPostSn(), t));
            }
            List<Tuple> ordered = postSns.stream().map(byPostSn::get).filter(java.util.Objects::nonNull).toList();
            resultVO.putResult("list", postQuery.decorate(ordered, me));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 최근 본 글 지우기 — 글 조회 기록만 지운다(커뮤니티 방문 기록은 추천에 쓰여 남긴다) */
    public ResultVO clearRecentView(Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            int n = jdbc.update("DELETE FROM knowra_user.tbl_user_action_log WHERE user_sn = ? AND target_type = ? AND action_type = ?",
                    me, TblUserActionLog.TARGET_POST, TblUserActionLog.ACTION_VIEW);
            resultVO.putResult("cleared", n);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.DELETE_ERROR.getMessage());
        }
        return resultVO;
    }
}
