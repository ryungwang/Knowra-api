package com.knowra.post.service;

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
    private final TblCommRepository tblCommRepository;
    private final TblCommMbrRepository tblCommMbrRepository;
    private final ModelMapper modelMapper;
    private final RedisApiService redisApiService;
    private final ActionLogService actionLogService;
    private final InterestScoreService interestScoreService;
    private final ApplicationEventPublisher eventPublisher;

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
            TblPost tblPost;
            if (isEdit) {
                tblPost = tblPostRepository.findById(Long.parseLong(params.get("postSn").toString()))
                        .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
                if (!canManage(tblPost, userSn)) {
                    resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                    resultVO.setResultMessage("수정 권한이 없습니다.");
                    return resultVO;
                }
                // updatable=false 컬럼(creatrSn, frstCrtDt, userSn)은 건드리지 않는다. 소속 커뮤니티도 바꾸지 않는다.
                tblPost.setPostTtl(params.get("postTtl").toString());
                tblPost.setPostCntnt(params.get("postCntnt").toString());
                tblPost.setPostTyp(resolvePostTyp(params, tblPost.getCommSn(), userSn, tblPost.getPostTyp()));
                tblPost.setMdfrSn(userSn);
            } else {
                tblPost = modelMapper.map(params, TblPost.class);
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
                tblPostRepository.save(tblPost);
                actionLogService.log(userSn, TblUserActionLog.TARGET_POST, tblPost.getPostSn(), TblUserActionLog.ACTION_POST);
                eventPublisher.publishEvent(new PostCreatedEvent(
                        tblPost.getPostSn(), userSn, tblPost.getCommSn(), tblPost.getFrstCrtDt()));
                notifyCommunityMembers(tblPost, userSn);
            }

            @SuppressWarnings("unchecked")
            List<String> tagNms = (List<String>) params.get("tagNms");
            if (isEdit) tagService.updateTag(tagNms, userSn, tblPost.getPostSn());
            else        tagService.setTag(tagNms, userSn, tblPost.getPostSn());

            resultVO.putResult("postSn", tblPost.getPostSn());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
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
            Tuple t = postQuery.base()
                    .where(POST.postSn.eq(postSn), PostQuery.alive(), PostQuery.visibleTo(me))
                    .fetchOne();
            if (t == null) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("게시글을 찾을 수 없습니다.");
                return resultVO;
            }
            PostDTO dto = postQuery.decorate(List.of(t), me).get(0);
            resultVO.putResult("post", dto);
            resultVO.putResult("myLikeTyp", dto.getMyLikeTyp());
            if (dto.getCommSn() != null) {
                TblComm community = tblCommRepository.findByCommSn(dto.getCommSn());
                resultVO.putResult("comm", community);
                resultVO.putResult("myRole", roleIn(dto.getCommSn(), me));
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
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
            e.printStackTrace();
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

            BooleanExpression condition = POST.commSn.eq(commSn).and(PostQuery.alive());
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
                // 첫 페이지: 공지는 항상 상단 고정, 일반 글은 남은 자리만큼
                List<Tuple> notices = postQuery.base()
                        .where(condition.and(POST.postTyp.eq("NOTICE")))
                        .orderBy(POST.postSn.desc())
                        .fetch();
                int normalLimit = Math.max(PAGE_SIZE - notices.size(), 0);
                List<Tuple> normals = postQuery.base()
                        .where(condition.and(POST.postTyp.eq("NORMAL")))
                        .orderBy(POST.postSn.desc())
                        .limit(normalLimit)
                        .fetch();
                tuples = new ArrayList<>(notices);
                tuples.addAll(normals);
            } else {
                tuples = postQuery.base()
                        .where(condition.and(POST.postTyp.eq("NORMAL")).and(POST.postSn.lt(cursor)))
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
            e.printStackTrace();
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
                .and(PostQuery.visibleTo(viewerSn));
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
                // 반대 반응으로 전환 (DOWN→UP: +2, UP→DOWN: -2)
                delta = up ? 2 : -2;
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
            e.printStackTrace();
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

            tblPostCmtRepository.save(TblPostCmt.builder()
                    .postSn(postSn)
                    .userSn(userSn)
                    .prntCmtSn(prntCmtSn)
                    .cmtCntnt(params.get("cmtCntnt").toString())
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
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
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
            if (cntnt.isEmpty()) throw new IllegalArgumentException("내용이 비어 있습니다.");

            TblPostCmt cmt = tblPostCmtRepository.findById(postCmtSn)
                    .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
            if (cmt.getUserSn() != userSn || !"ACTIVE".equals(cmt.getStat()) || !"Y".equals(cmt.getActvtnYn())) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("수정 권한이 없습니다.");
                return resultVO;
            }
            cmt.setCmtCntnt(cntnt);
            cmt.setMdfrSn(userSn);
            tblPostCmtRepository.save(cmt);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
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
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
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
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
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
            if (cursor != null) rootCondition = rootCondition.and(cmt.postCmtSn.gt(cursor));

            List<Tuple> rootTuples = q.select(cmt, cmtUser.nickName, cmtUser.name,
                            cmtPfpFile.atchFilePathNm, cmtPfpFile.strgFileNm, cmtPfpFile.atchFileExtnNm)
                    .from(cmt)
                    .join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn))
                    .leftJoin(cmtUser.pfp, cmtPfpFile)
                    .where(rootCondition)
                    .orderBy(cmt.postCmtSn.asc())
                    .limit(size)
                    .fetch();

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
                            cmtPfpFile.atchFilePathNm, cmtPfpFile.strgFileNm, cmtPfpFile.atchFileExtnNm)
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
                String     pfpUrl = pathNm != null
                        ? pathNm + "/" + t.get(cmtPfpFile.strgFileNm) + "." + t.get(cmtPfpFile.atchFileExtnNm) : null;
                boolean deleted = !"ACTIVE".equals(c.getStat());
                return new CmtDTO(
                        sn, c.getUserSn(), t.get(cmtUser.nickName), t.get(cmtUser.name),
                        deleted ? "" : c.getCmtCntnt(),   // 지워진 댓글의 본문은 내보내지 않는다
                        c.getLikeCnt(), c.getFrstCrtDt(), new ArrayList<>(),
                        reactionsMap.getOrDefault(sn, new HashMap<>()),
                        myReactMap.get(sn), pfpUrl,
                        deleted ? "DELETED" : "ACTIVE", c.getMdfcnDt());
            };
            Map<Long, CmtDTO> rootMap = new LinkedHashMap<>();
            rootTuples.forEach(t -> rootMap.put(t.get(cmt).getPostCmtSn(), toDto.apply(t)));
            replyTuples.forEach(t -> {
                CmtDTO parent = rootMap.get(t.get(cmt).getPrntCmtSn());
                if (parent != null) parent.getReplies().add(toDto.apply(t));
            });
            List<CmtDTO> list = new ArrayList<>(rootMap.values());

            // ── 5. nextCursor: 마지막 루트 댓글 SN (size 미달이면 null) ────────
            Long nextCursor = list.size() == size ? rootSns.get(rootSns.size() - 1) : null;
            resultVO.putResult("list", list);
            resultVO.putResult("nextCursor", nextCursor);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
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

            TblPostSave existing = tblPostSaveRepository.findByUserSnAndPostSn(userSn, postSn);
            if (isSave) {
                if (existing == null) {
                    TblPostSave save = new TblPostSave();
                    save.setUserSn(userSn);
                    save.setPostSn(postSn);
                    save.setCreatrSn(userSn);
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
            e.printStackTrace();
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
            if (cursor != null) condition = condition.and(qSave.postSaveSn.lt(cursor));
            List<Tuple> saves = new JPAQueryFactory(em)
                    .select(qSave.postSaveSn, qSave.postSn)
                    .from(qSave)
                    .where(condition)
                    .orderBy(qSave.postSaveSn.desc())
                    .limit(size)
                    .fetch();

            List<Long> postSns = saves.stream().map(t -> t.get(qSave.postSn)).toList();
            Map<Long, Tuple> byPostSn = new HashMap<>();
            if (!postSns.isEmpty()) {
                postQuery.base()
                        .where(POST.postSn.in(postSns), PostQuery.alive())
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
            resultVO.putResult("list", list);
            resultVO.putResult("nextCursor", nextCursor);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }
}
