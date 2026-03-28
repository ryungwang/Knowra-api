package com.knowra.common.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.common.entity.QTblPost;
import com.knowra.common.entity.QTblPostTag;
import com.knowra.common.entity.QTblTag;
import com.knowra.common.entity.TblPost;
import com.knowra.common.repository.TblPostLikeRepository;
import com.knowra.user.entity.QTblUser;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    @PersistenceContext
    private EntityManager em;

    private final JwtProvider jwtProvider;
    private final TblPostLikeRepository tblPostLikeRepository;

    public ResultVO getPostList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            Long          userSn       = params.get("userSn") != null ? Long.parseLong(params.get("userSn").toString()) : null;
            Long          viewerUserSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            LocalDateTime cursor       = params.get("cursor") != null ? LocalDateTime.parse(params.get("cursor").toString()) : null;
            int           size         = params.get("size")   != null ? Integer.parseInt(params.get("size").toString()) : 50;

            QTblPost    qPost = QTblPost.tblPost;
            QTblUser    qUser = QTblUser.tblUser;
            QTblPostTag qTag  = QTblPostTag.tblPostTag;
            QTblTag     qTbl  = QTblTag.tblTag;

            var condition = qPost.stat.eq("ACTIVE").and(qPost.actvtnYn.eq("Y"));
            if (userSn != null) condition = condition.and(qPost.userSn.eq(userSn));
            if (cursor  != null) condition = condition.and(qPost.frstCrtDt.lt(cursor));

            List<com.querydsl.core.Tuple> tuples = new JPAQueryFactory(em)
                    .select(qPost, qUser.name)
                    .from(qPost)
                    .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                    .where(condition)
                    .orderBy(qPost.frstCrtDt.desc())
                    .limit(size)
                    .fetch();

            if (tuples.isEmpty()) {
                resultVO.putResult("list",       List.of());
                resultVO.putResult("nextCursor", null);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }

            List<Long> postSns = tuples.stream().map(t -> t.get(qPost).getPostSn()).toList();

            // 태그 배치 조회
            Map<Long, List<String>> tagMap = new HashMap<>();
            new JPAQueryFactory(em)
                    .select(qTag.postSn, qTbl.tagNm)
                    .from(qTag).join(qTbl).on(qTag.tagSn.eq(qTbl.tagSn))
                    .where(qTag.postSn.in(postSns))
                    .fetch()
                    .forEach(t -> tagMap.computeIfAbsent(t.get(qTag.postSn), k -> new ArrayList<>())
                            .add(t.get(qTbl.tagNm)));

            // 내 좋아요 배치 조회 (TblPostLike는 likeTyp 없음 → actvtnYn "Y" 로 반환)
            java.util.Set<Long> likedSet = new java.util.HashSet<>();
            if (viewerUserSn != null) {
                tblPostLikeRepository.findByPostSnInAndUserSn(postSns, viewerUserSn)
                        .stream()
                        .filter(l -> "Y".equals(l.getActvtnYn()))
                        .forEach(l -> likedSet.add(l.getPostSn()));
            }

            List<Map<String, Object>> list = tuples.stream().map(t -> {
                TblPost p = t.get(qPost);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("postType",  "POST");
                map.put("postSn",    p.getPostSn());
                map.put("userSn",    p.getUserSn());
                map.put("authorNm",  t.get(qUser.name));
                map.put("postTtl",   p.getPostTtl());
                map.put("frstCrtDt", p.getFrstCrtDt());
                map.put("viewCnt",   p.getViewCnt());
                map.put("likeCnt",   p.getLikeCnt());
                map.put("cmtCnt",    p.getCmtCnt());
                map.put("tagNms",    tagMap.getOrDefault(p.getPostSn(), List.of()));
                map.put("myLikeTyp", likedSet.contains(p.getPostSn()) ? "Y" : null);
                return map;
            }).toList();

            resultVO.putResult("list",       list);
            resultVO.putResult("nextCursor", list.size() == size ? list.getLast().get("frstCrtDt").toString() : null);
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
