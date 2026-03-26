package com.knowra.community.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.PaginationInfo;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.entity.QTblTag;
import com.knowra.common.entity.TblTag;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.common.repository.TblTagRepository;
import com.knowra.common.entity.CmtDTO;
import com.knowra.community.entity.*;
import com.knowra.community.repository.*;
import com.knowra.user.entity.QTblUser;
import org.modelmapper.ModelMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional
public class CommunityPostService {
    private final ModelMapper modelMapper;
    private final TblCommPostRepository tblCommPostRepository;
    private final TblCommPostTagRepository tblCommPostTagRepository;
    private final TblCommPostLikeRepository tblCommPostLikeRepository;
    private final TblTagRepository tblTagRepository;
    private final RedisApiService redisApiService;
    private final JwtProvider jwtProvider;

    private static final int REDIS_DB = 15;

    @PersistenceContext
    private EntityManager em;

    @Value("${Globals.pageSize}")
    private int pageSize;

    @Value("${Globals.pageUnit}")
    private int pageUnit;

    public ResultVO setCommPost(Map<String, Object> params, String token) {

        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            TblCommPost tblCommPost = modelMapper.map(params, TblCommPost.class);
            tblCommPost.setUserSn(userSn);
            tblCommPost.setCreatrSn(userSn);
            tblCommPostRepository.save(tblCommPost);

            List<String> tagNms = (List<String>) params.get("tagNms");
            for (String tagNm : tagNms) {
                TblTag tblTag = tblTagRepository.findByTagNm(tagNm);
                if(tblTag != null){
                    tblTag.setUseCount(tblTag.getUseCount() + 1);
                    tblTagRepository.save(tblTag);
                    TblCommPostTag tblCommPostTag = TblCommPostTag.builder()
                            .commPostSn(tblCommPost.getCommPostSn())
                            .tagSn(tblTag.getTagSn())
                            .build();
                    tblCommPostTagRepository.save(tblCommPostTag);
                }else{
                    tblTag = tblTagRepository.save(TblTag.builder().tagNm(tagNm).useCount(1).build());
                    tblTag.setCreatrSn(userSn);
                    TblCommPostTag tblCommPostTag = TblCommPostTag.builder()
                            .commPostSn(tblCommPost.getCommPostSn())
                            .tagSn(tblTag.getTagSn())
                            .build();
                    tblCommPostTagRepository.save(tblCommPostTag);
                }
            }

            resultVO.putResult("commPostSn", tblCommPost.getCommPostSn());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO viewCommPost(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long commPostSn = Long.parseLong(params.get("commPostSn").toString());
            redisApiService.incrementViewCount(REDIS_DB, commPostSn);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO getCommPostList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            Long userSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            JPAQueryFactory q = new JPAQueryFactory(em);

            long commSn = Long.parseLong(params.get("commSn").toString());

            QTblCommPost post = QTblCommPost.tblCommPost;
            QTblCommPostTag postTag = QTblCommPostTag.tblCommPostTag;
            QTblTag tag = QTblTag.tblTag;

            String listTyp = params.get("listTyp") != null ? params.get("listTyp").toString() : "LATEST";
            Long cursor = params.get("cursor") != null ? Long.parseLong(params.get("cursor").toString()) : null;
            int page = params.get("page") != null ? Integer.parseInt(params.get("page").toString()) : 0;

            QTblUser user = QTblUser.tblUser;
            var condition = post.commSn.eq(commSn)
                    .and(post.stat.eq("ACTIVE"))
                    .and(post.actvtnYn.eq("Y"));

            List<com.querydsl.core.Tuple> tuples;

            if ("POPULAR".equals(listTyp)) {
                // 인기: likeCnt 내림차순, offset 페이징
                tuples = q.select(post, user.name)
                        .from(post)
                        .join(user).on(post.userSn.eq(user.userSn))
                        .where(condition)
                        .orderBy(post.likeCnt.desc(), post.commPostSn.desc())
                        .offset((long) page * 20)
                        .limit(20)
                        .fetch();
            } else if ("NOTICE".equals(listTyp)) {
                // 공지: postTyp = NOTICE, 커서 페이징
                condition = condition.and(post.postTyp.eq("NOTICE"));
                if (cursor != null) condition = condition.and(post.commPostSn.lt(cursor));
                tuples = q.select(post, user.name)
                        .from(post)
                        .join(user).on(post.userSn.eq(user.userSn))
                        .where(condition)
                        .orderBy(post.commPostSn.desc())
                        .limit(20)
                        .fetch();
            } else {
                // ALL / LATEST: 최신순 커서 페이징
                if (cursor != null) condition = condition.and(post.commPostSn.lt(cursor));
                tuples = q.select(post, user.name)
                        .from(post)
                        .join(user).on(post.userSn.eq(user.userSn))
                        .where(condition)
                        .orderBy(post.commPostSn.desc())
                        .limit(20)
                        .fetch();
            }

            List<Long> postSns = tuples.stream()
                    .map(t -> t.get(post).getCommPostSn())
                    .collect(java.util.stream.Collectors.toList());

            // 태그 일괄 조회
            Map<Long, List<String>> tagMap = new java.util.HashMap<>();
            if (!postSns.isEmpty()) {
                q.select(postTag.commPostSn, tag.tagNm)
                 .from(postTag)
                 .join(tag).on(postTag.tagSn.eq(tag.tagSn))
                 .where(postTag.commPostSn.in(postSns))
                 .fetch()
                 .forEach(t -> tagMap
                     .computeIfAbsent(t.get(postTag.commPostSn), k -> new ArrayList<>())
                     .add(t.get(tag.tagNm)));
            }

            // 내 좋아요 일괄 조회 (postSn → likeTyp 맵)
            Map<Long, String> likeMap = new java.util.HashMap<>();
            if (!postSns.isEmpty() && userSn != null) {
                tblCommPostLikeRepository.findByCommPostSnInAndUserSn(postSns, userSn)
                        .forEach(like -> likeMap.put(like.getCommPostSn(), like.getLikeTyp()));
            }

            // DTO 조립
            List<CommunityPostDTO> list = new ArrayList<>();
            for (com.querydsl.core.Tuple t : tuples) {
                TblCommPost p = t.get(post);
                list.add(new CommunityPostDTO(
                        p.getCommPostSn(), p.getCommSn(), p.getUserSn(),
                        t.get(user.name), p.getPostTyp(), p.getPostTtl(), null, p.getFrstCrtDt(),
                        p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(),
                        tagMap.getOrDefault(p.getCommPostSn(), List.of()),
                        likeMap.get(p.getCommPostSn())
                ));
            }

            Long nextCursor = list.size() == 20
                    ? list.get(list.size() - 1).getCommPostSn()
                    : null;

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

    public ResultVO getCommPost(TblCommPost tblCommPost, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            Long userSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            JPAQueryFactory q = new JPAQueryFactory(em);
            long commPostSn = tblCommPost.getCommPostSn();

            QTblCommPost post = QTblCommPost.tblCommPost;
            QTblCommPostTag postTag = QTblCommPostTag.tblCommPostTag;
            QTblTag tag = QTblTag.tblTag;
            QTblUser user = QTblUser.tblUser;
            QTblComm comm = QTblComm.tblComm;
            QTblCommPostCmt cmt = QTblCommPostCmt.tblCommPostCmt;

            // 게시글 + 작성자
            com.querydsl.core.Tuple postTuple = q.select(post, user.name)
                    .from(post)
                    .join(user).on(post.userSn.eq(user.userSn))
                    .where(post.commPostSn.eq(commPostSn), post.stat.eq("ACTIVE"))
                    .fetchOne();

            if (postTuple == null) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("게시글을 찾을 수 없습니다.");
                return resultVO;
            }

            TblCommPost p = postTuple.get(post);

            // 커뮤니티 (memberCnt 캐시 포함)
            TblComm community = q.selectFrom(comm)
                    .where(comm.commSn.eq(p.getCommSn()))
                    .fetchOne();


            // 태그
            List<String> tagNms = q.select(tag.tagNm)
                    .from(postTag)
                    .join(tag).on(postTag.tagSn.eq(tag.tagSn))
                    .where(postTag.commPostSn.eq(commPostSn))
                    .fetch();

            // 댓글 + 작성자 (부모/대댓글 한번에)
            QTblUser cmtUser = new QTblUser("cmtUser");
            List<com.querydsl.core.Tuple> cmtTuples = q.select(cmt, cmtUser.name)
                    .from(cmt)
                    .join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn))
                    .where(cmt.commPostSn.eq(commPostSn), cmt.stat.eq("ACTIVE"), cmt.actvtnYn.eq("Y"))
                    .orderBy(cmt.commPostCmtSn.asc())
                    .fetch();

            // 댓글 트리 조립 (부모 → 대댓글)
            Map<Long, CmtDTO> cmtMap = new java.util.LinkedHashMap<>();
            List<CmtDTO> rootCmts = new ArrayList<>();

            for (com.querydsl.core.Tuple t : cmtTuples) {
                TblCommPostCmt c = t.get(cmt);
                CmtDTO dto = new CmtDTO(
                        c.getCommPostCmtSn(), c.getUserSn(), t.get(cmtUser.name),
                        c.getCmtCntnt(), c.getLikeCnt(), c.getFrstCrtDt(), new ArrayList<>()
                );
                cmtMap.put(c.getCommPostCmtSn(), dto);
                if (c.getPrntCmtSn() == null) {
                    rootCmts.add(dto);
                } else {
                    CmtDTO parent = cmtMap.get(c.getPrntCmtSn());
                    if (parent != null) parent.getReplies().add(dto);
                }
            }

            // 내 좋아요 여부 (UP / DOWN / null)
            TblCommPostLike myLike = userSn != null
                    ? tblCommPostLikeRepository.findByCommPostSnAndUserSn(commPostSn, userSn)
                    : null;
            String myLikeTyp = myLike != null ? myLike.getLikeTyp() : null;

            resultVO.putResult("comm", community);
            resultVO.putResult("post", new CommunityPostDTO(
                    p.getCommPostSn(), p.getCommSn(), p.getUserSn(),
                    postTuple.get(user.name), p.getPostTyp(), p.getPostTtl(), p.getPostCntnt(), p.getFrstCrtDt(),
                    p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(), tagNms, myLikeTyp
            ));
            resultVO.putResult("myLikeTyp", myLikeTyp);
            resultVO.putResult("comments", rootCmts);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO setCommPostLike(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long commPostSn = Long.parseLong(params.get("commPostSn").toString());
            String postLike = params.get("postLike").toString().toUpperCase();

            TblCommPost commPost = tblCommPostRepository.findById(commPostSn).orElseThrow();
            TblCommPostLike commPostLike = tblCommPostLikeRepository.findByCommPostSnAndUserSn(commPostSn, userSn);

            if(postLike.equals("UP")){
                commPost.setLikeCnt(commPost.getLikeCnt() + 1);
            }else{
                commPost.setLikeCnt(commPost.getLikeCnt() - 1);
            }
            tblCommPostRepository.save(commPost);

            if(commPostLike == null){
                commPostLike = TblCommPostLike.builder()
                        .userSn(userSn)
                        .commPostSn(commPostSn)
                        .likeTyp(postLike)
                        .creatrSn(userSn)
                        .build();
            }else{
                commPostLike.setLikeTyp(postLike);
            }
            tblCommPostLikeRepository.save(commPostLike);

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }
}
