package com.knowra.search.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.entity.QTblTag;
import com.knowra.community.entity.QTblComm;
import com.knowra.post.entity.PostDTO;
import com.knowra.post.entity.QTblPostTag;
import com.knowra.post.service.PostQuery;
import com.knowra.user.entity.QTblUser;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.knowra.post.service.PostQuery.POST;

/**
 * 통합 검색 — 글·커뮤니티·사용자·태그를 한 번에.
 *
 * <p>탐색 화면이 그동안 가짜 데이터로 검색하는 척을 하고 있었다(헤더 검색창이 거기로 보냈다).
 * 전문 검색 엔진 없이 LIKE 로 간다 — 지금 규모(글 수백)에서는 충분하고, 커지면 이 클래스만 바꾼다.
 * 검색어가 비어 있으면 인기 태그만 돌려준다(탐색 화면의 기본 상태).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    @PersistenceContext
    private EntityManager em;

    private final PostQuery postQuery;

    private static final int LIMIT_POSTS = 20;
    private static final int LIMIT_OTHER = 10;

    public ResultVO search(String rawQuery, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            String q = rawQuery == null ? "" : rawQuery.trim().replaceFirst("^#", "");
            if (q.isBlank()) {
                resultVO.putResult("popularTags", popularTags());
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }
            resultVO.putResult("posts",       searchPosts(q, me));
            resultVO.putResult("communities", searchCommunities(q, me));
            resultVO.putResult("users",       searchUsers(q));
            resultVO.putResult("tags",        searchTags(q));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 제목·본문·태그 중 하나라도 맞는 글. 태그는 "#"을 떼고 비교한다. */
    private List<PostDTO> searchPosts(String q, Long me) {
        QTblPostTag qPostTag = QTblPostTag.tblPostTag;
        QTblTag     qTag     = QTblTag.tblTag;
        BooleanExpression byTag = POST.postSn.in(
                JPAExpressions.select(qPostTag.postSn).from(qPostTag)
                        .join(qTag).on(qPostTag.tagSn.eq(qTag.tagSn))
                        .where(qTag.tagNm.containsIgnoreCase(q)));
        List<Tuple> tuples = postQuery.base()
                .where(PostQuery.alive(), PostQuery.visibleTo(me),
                       POST.postTtl.containsIgnoreCase(q)
                               .or(POST.postCntnt.containsIgnoreCase(q))
                               .or(byTag))
                .orderBy(POST.frstCrtDt.desc())
                .limit(LIMIT_POSTS)
                .fetch();
        return postQuery.decorate(tuples, me);
    }

    /** 비공개(private) 커뮤니티는 멤버에게만. 승인제(restricted)는 이름이 보여야 신청할 수 있다. */
    private List<Map<String, Object>> searchCommunities(String q, Long me) {
        QTblComm    qComm = QTblComm.tblComm;
        QTblComFile qLogo = new QTblComFile("logoFile");
        BooleanExpression discoverable = qComm.prvcyStng.ne("private");
        if (me != null) {
            discoverable = discoverable.or(com.knowra.common.CommunityQueryHelper.accessCondition(qComm, me));
        }
        List<Tuple> rows = new JPAQueryFactory(em)
                .select(qComm.commSn, qComm.commNm, qComm.commDsplNm, qComm.commDesc, qComm.memberCnt, qComm.prvcyStng, qComm.logoFile)
                .from(qComm)
                .leftJoin(qComm.logoFile, qLogo)
                .where(qComm.actvtnYn.eq("Y"), qComm.stat.eq("Y"), discoverable,
                       qComm.commDsplNm.containsIgnoreCase(q)
                               .or(qComm.commNm.containsIgnoreCase(q))
                               .or(qComm.commDesc.containsIgnoreCase(q)))
                .orderBy(qComm.memberCnt.desc())
                .limit(LIMIT_OTHER)
                .fetch();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("commSn",     t.get(qComm.commSn));
            m.put("commNm",     t.get(qComm.commNm));
            m.put("commDsplNm", t.get(qComm.commDsplNm));
            m.put("commDesc",   t.get(qComm.commDesc));
            m.put("memberCnt",  t.get(qComm.memberCnt));
            m.put("prvcyStng",  t.get(qComm.prvcyStng));
            m.put("logoFile",   t.get(qComm.logoFile));
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> searchUsers(String q) {
        QTblUser    qUser = QTblUser.tblUser;
        QTblComFile qPfp  = new QTblComFile("pfpFile");
        List<Tuple> rows = new JPAQueryFactory(em)
                .select(qUser.userSn, qUser.nickName, qUser.name, qUser.bio,
                        qPfp.atchFilePathNm, qPfp.strgFileNm, qPfp.atchFileExtnNm)
                .from(qUser)
                .leftJoin(qUser.pfp, qPfp)
                .where(qUser.actvtnYn.eq("Y"), qUser.status.eq("Y"),
                       qUser.nickName.containsIgnoreCase(q)
                               .or(qUser.name.containsIgnoreCase(q))
                               .or(qUser.bio.containsIgnoreCase(q)))
                .orderBy(qUser.nickName.asc())
                .limit(LIMIT_OTHER)
                .fetch();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userSn",   t.get(qUser.userSn));
            m.put("nickName", t.get(qUser.nickName));
            m.put("name",     t.get(qUser.name));
            m.put("bio",      t.get(qUser.bio));
            String pathNm = t.get(qPfp.atchFilePathNm);
            m.put("pfpUrl", pathNm != null ? pathNm + "/" + t.get(qPfp.strgFileNm) + "." + t.get(qPfp.atchFileExtnNm) : null);
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> searchTags(String q) {
        QTblTag qTag = QTblTag.tblTag;
        return toTagMaps(new JPAQueryFactory(em)
                .select(qTag.tagSn, qTag.tagNm, qTag.useCount)
                .from(qTag)
                .where(qTag.actvtnYn.eq("Y"), qTag.tagNm.containsIgnoreCase(q))
                .orderBy(qTag.useCount.desc())
                .limit(LIMIT_OTHER)
                .fetch(), qTag);
    }

    private List<Map<String, Object>> popularTags() {
        QTblTag qTag = QTblTag.tblTag;
        return toTagMaps(new JPAQueryFactory(em)
                .select(qTag.tagSn, qTag.tagNm, qTag.useCount)
                .from(qTag)
                .where(qTag.actvtnYn.eq("Y"), qTag.useCount.gt(0))
                .orderBy(qTag.useCount.desc())
                .limit(24)
                .fetch(), qTag);
    }

    private static List<Map<String, Object>> toTagMaps(List<Tuple> rows, QTblTag qTag) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Tuple t : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tagSn",    t.get(qTag.tagSn));
            m.put("tagNm",    t.get(qTag.tagNm));
            m.put("useCount", t.get(qTag.useCount));
            list.add(m);
        }
        return list;
    }
}
