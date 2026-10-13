package com.knowra.post.service;

import com.knowra.common.CommunityQueryHelper;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.service.TagService;
import com.knowra.community.entity.QTblComm;
import com.knowra.post.entity.PostDTO;
import com.knowra.post.entity.QTblPost;
import com.knowra.post.entity.QTblPostLike;
import com.knowra.post.entity.QTblPostSave;
import com.knowra.post.entity.TblPost;
import com.knowra.user.entity.QTblUser;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 게시글 목록 조회의 공통 뼈대.
 *
 * <p>글 + 작성자 + 프로필 사진 + (있으면) 커뮤니티를 한 번에 가져오고, 태그·내 좋아요·
 * 내 저장은 배치로 붙인다. 글 모델을 합치기 전에는 이 모양의 쿼리가 서비스마다 두 벌씩
 * (일반 글 / 커뮤니티 글) 복사되어 있었다 — 조건과 정렬만 다르고 나머지는 전부 같았다.
 * 호출부는 {@link #base()} 에 where/orderBy/limit 만 얹고 {@link #decorate} 로 마무리한다.
 */
@Component
@RequiredArgsConstructor
public class PostQuery {

    @PersistenceContext
    private EntityManager em;

    private final TagService tagService;

    public static final QTblPost    POST = QTblPost.tblPost;
    public static final QTblComm    COMM = QTblComm.tblComm;
    public static final QTblUser    USER = QTblUser.tblUser;
    public static final QTblComFile PFP  = new QTblComFile("pfpFile");

    /** 살아 있는 글 — 삭제(actvtnYn=N)·차단(stat≠ACTIVE) 제외. */
    public static BooleanExpression alive() {
        return POST.stat.eq("ACTIVE").and(POST.actvtnYn.eq("Y"));
    }

    /**
     * viewer 가 볼 수 있는 글 — 전체 공개 글이거나, 공개 커뮤니티거나, viewer 가 멤버인
     * 커뮤니티의 글. {@link #base()} 가 커뮤니티를 left join 하므로 그 alias 를 쓴다.
     */
    public static BooleanExpression visibleTo(Long viewerSn) {
        BooleanExpression access = POST.commSn.isNull().or(CommunityQueryHelper.accessCondition(COMM, viewerSn));
        if (viewerSn == null) return access;
        // 차단은 양방향 — 내가 차단한 사람의 글도, 나를 차단한 사람의 글도 안 보인다. 숨긴 글은 내게만 안 보인다.
        com.knowra.safety.entity.QTblUserBlock blk  = com.knowra.safety.entity.QTblUserBlock.tblUserBlock;
        com.knowra.safety.entity.QTblPostHide  hide = com.knowra.safety.entity.QTblPostHide.tblPostHide;
        return access
                .and(POST.userSn.notIn(JPAExpressions.select(blk.blockedSn).from(blk).where(blk.userSn.eq(viewerSn))))
                .and(POST.userSn.notIn(JPAExpressions.select(blk.userSn).from(blk).where(blk.blockedSn.eq(viewerSn))))
                .and(POST.postSn.notIn(JPAExpressions.select(hide.postSn).from(hide).where(hide.userSn.eq(viewerSn))));
    }

    public JPAQuery<Tuple> base() {
        return new JPAQueryFactory(em)
                .select(POST, USER.nickName, USER.name, COMM.commNm, COMM.commDsplNm,
                        PFP.atchFilePathNm, PFP.strgFileNm, PFP.atchFileExtnNm)
                .from(POST)
                .join(USER).on(POST.userSn.eq(USER.userSn))
                .leftJoin(USER.pfp, PFP)
                .leftJoin(COMM).on(POST.commSn.eq(COMM.commSn));
    }

    /** 조회 행 하나 → DTO. 태그·좋아요·저장은 비어 있다 — 목록은 {@link #decorate} 로. */
    public PostDTO toDto(Tuple t) {
        TblPost p = t.get(POST);
        PostDTO dto = new PostDTO(
                p.getPostTyp(), p.getPostSn(),
                p.getCommSn(), t.get(COMM.commNm), t.get(COMM.commDsplNm),
                p.getUserSn(), t.get(USER.nickName), t.get(USER.name),
                p.getPostTtl(), p.getPostCntnt(), p.getFrstCrtDt(),
                p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(),
                null, false);
        dto.setPfpUrl(pfpUrl(t));
        return dto;
    }

    public static String pfpUrl(Tuple t) {
        String pathNm = t.get(PFP.atchFilePathNm);
        return pathNm != null
                ? pathNm + "/" + t.get(PFP.strgFileNm) + "." + t.get(PFP.atchFileExtnNm)
                : null;
    }

    /** 조회 행들 → DTO 목록 + 태그·내 좋아요·내 저장(배치 3회). 순서는 입력 그대로. */
    public List<PostDTO> decorate(List<Tuple> tuples, Long viewerSn) {
        List<PostDTO> list = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) list.add(toDto(t));
        return decorateDtos(list, viewerSn);
    }

    public List<PostDTO> decorateDtos(List<PostDTO> list, Long viewerSn) {
        if (list.isEmpty()) return list;
        List<Long> postSns = list.stream().map(PostDTO::getPostSn).toList();
        Map<Long, List<String>> tagMap   = tagService.fetchTagMap(postSns);
        Map<Long, String>       likeMap  = viewerSn != null ? myLikeMap(viewerSn, postSns)  : Map.of();
        Set<Long>               savedSet = viewerSn != null ? mySavedSet(viewerSn, postSns) : Set.of();
        for (PostDTO dto : list) {
            dto.setTagNms(tagMap.getOrDefault(dto.getPostSn(), List.of()));
            dto.setMyLikeTyp(likeMap.get(dto.getPostSn()));
            dto.setMySaved(savedSet.contains(dto.getPostSn()));
        }
        return list;
    }

    /** postSn → UP/DOWN */
    public Map<Long, String> myLikeMap(long viewerSn, List<Long> postSns) {
        if (postSns.isEmpty()) return Map.of();
        QTblPostLike qLike = QTblPostLike.tblPostLike;
        Map<Long, String> result = new HashMap<>();
        new JPAQueryFactory(em)
                .select(qLike.postSn, qLike.likeTyp)
                .from(qLike)
                .where(qLike.userSn.eq(viewerSn).and(qLike.postSn.in(postSns)))
                .fetch()
                .forEach(t -> result.put(t.get(qLike.postSn), t.get(qLike.likeTyp)));
        return result;
    }

    public Set<Long> mySavedSet(long viewerSn, List<Long> postSns) {
        if (postSns.isEmpty()) return Set.of();
        QTblPostSave qSave = QTblPostSave.tblPostSave;
        return new HashSet<>(new JPAQueryFactory(em)
                .select(qSave.postSn)
                .from(qSave)
                .where(qSave.userSn.eq(viewerSn)
                        .and(qSave.postSn.in(postSns))
                        .and(qSave.actvtnYn.eq("Y")))
                .fetch());
    }
}
