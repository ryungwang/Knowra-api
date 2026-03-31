package com.knowra.post.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.common.entity.*;
import com.knowra.common.service.TagService;
import com.knowra.community.entity.*;
import com.knowra.community.repository.TblCommPostRepository;
import com.knowra.post.entity.*;
import com.knowra.post.repository.*;
import com.knowra.post.entity.QTblPostSave;
import com.knowra.user.entity.QTblUser;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    @PersistenceContext
    private EntityManager em;

    private final JwtProvider jwtProvider;
    private final TagService tagService;
    private final TblPostRepository tblPostRepository;
    private final TblPostLikeRepository tblPostLikeRepository;
    private final TblPostCmtRepository tblPostCmtRepository;
    private final TblPostCmtReactRepository tblPostCmtReactRepository;

    private final TblPostSaveRepository tblPostSaveRepository;
    private final TblCommPostRepository tblCommPostRepository;
    private final ModelMapper modelMapper;
    private final RedisApiService redisApiService;
    private static final int REDIS_DB = 15;

    public ResultVO setPost(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            TblPost tblPost;
            if(params.get("commPostSn") != null){
                // 수정 — updatable=false 컬럼(creatrSn, frstCrtDt, userSn)은 건드리지 않음
                tblPost = tblPostRepository.findById(Long.parseLong(params.get("postSn").toString()))
                        .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
                tblPost.setPostTtl(params.get("postTtl").toString());
                tblPost.setPostCntnt(params.get("postCntnt").toString());
                tblPost.setMdfrSn(userSn);
            }else{
                tblPost = modelMapper.map(params, TblPost.class);
                tblPost.setUserSn(userSn);
                tblPost.setCreatrSn(userSn);
                tblPostRepository.save(tblPost);
            }

            @SuppressWarnings("unchecked")
            List<String> tagNms = (List<String>) params.get("tagNms");
            if(params.get("postSn") != null){
                tagService.updateTag(tagNms, userSn, "post", tblPost.getPostSn());
            }else{
                tagService.setTag(tagNms, userSn, "post", tblPost.getPostSn());
            }

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

    public ResultVO getPost(TblPost tblPost, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            Long userSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            JPAQueryFactory q = new JPAQueryFactory(em);
            long postSn = tblPost.getPostSn();

            QTblPost post = QTblPost.tblPost;
            QTblUser user = QTblUser.tblUser;

            // 게시글 + 작성자
            com.querydsl.core.Tuple postTuple = q.select(post, user.name, user.loginId)
                    .from(post)
                    .join(user).on(post.userSn.eq(user.userSn))
                    .where(post.postSn.eq(postSn), post.stat.eq("ACTIVE"))
                    .fetchOne();

            if (postTuple == null) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("게시글을 찾을 수 없습니다.");
                return resultVO;
            }

            TblPost p = postTuple.get(post);

            // 태그
            List<String> tagNms = tagService.fetchTagMap(List.of(postSn), "post").getOrDefault(postSn, List.of());

            // 내 좋아요 여부 (UP / DOWN / null)
            TblPostLike myLike = userSn != null
                    ? tblPostLikeRepository.findByPostSnAndUserSn(postSn, userSn)
                    : null;
            String myLikeTyp = myLike != null ? myLike.getLikeTyp() : null;

            boolean mySaved = userSn != null &&
                    tblPostSaveRepository.findByUserSnAndPostSnAndPostKind(userSn, postSn, "POST") != null;

            PostDTO postDTO = new PostDTO(
                    "POST", p.getPostTyp(), p.getPostSn(), p.getUserSn(),
                    postTuple.get(user.loginId), postTuple.get(user.name),
                    p.getPostTtl(), p.getPostCntnt(), p.getFrstCrtDt(),
                    p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(), myLikeTyp, mySaved
            );
            postDTO.setTagNms(tagNms);
            resultVO.putResult("post", postDTO);
            resultVO.putResult("myLikeTyp", myLikeTyp);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO viewPost(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            long postSn = Long.parseLong(params.get("postSn").toString());
            redisApiService.incrementViewCount(REDIS_DB, params.get("type").toString(), postSn);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO setPostLike(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            JPAQueryFactory q = new JPAQueryFactory(em);

            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long postSn = Long.parseLong(params.get("postSn").toString());
            String postLike = params.get("postLike").toString().toUpperCase();

            QTblPost qPost = QTblPost.tblPost;
            TblPostLike tblPostLike = tblPostLikeRepository.findByPostSnAndUserSn(postSn, userSn);

            int delta;
            if (tblPostLike == null) {
                // 처음 반응 → 추가
                delta = "UP".equals(postLike) ? 1 : -1;
                tblPostLikeRepository.save(TblPostLike.builder()
                        .userSn(userSn)
                        .postSn(postSn)
                        .likeTyp(postLike)
                        .creatrSn(userSn)
                        .build());
            } else if (tblPostLike.getLikeTyp().equals(postLike)) {
                // 같은 반응 재클릭 → 취소
                delta = "UP".equals(postLike) ? -1 : 1;
                tblPostLikeRepository.delete(tblPostLike);
            } else {
                // 반대 반응으로 전환 (DOWN→UP: +2, UP→DOWN: -2)
                delta = "UP".equals(postLike) ? 2 : -2;
                tblPostLike.setLikeTyp(postLike);
                tblPostLikeRepository.save(tblPostLike);
            }

            q.update(qPost)
                    .set(qPost.likeCnt, qPost.likeCnt.add(delta))
                    .where(qPost.postSn.eq(postSn))
                    .execute();

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO setPostCmt(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long postSn = Long.parseLong(params.get("postSn").toString());
            Long prntCmtSn = params.get("prntCmtSn") != null
                    ? Long.parseLong(params.get("prntCmtSn").toString()) : null;

            TblPostCmt cmt = TblPostCmt.builder()
                    .postSn(postSn)
                    .userSn(userSn)
                    .prntCmtSn(prntCmtSn)
                    .cmtCntnt(params.get("cmtCntnt").toString())
                    .creatrSn(userSn)
                    .build();
            tblPostCmtRepository.save(cmt);

            // 댓글수 +1
            JPAQueryFactory q = new JPAQueryFactory(em);
            QTblPost qPost = QTblPost.tblPost;
            q.update(qPost)
                    .set(qPost.cmtCnt, qPost.cmtCnt.add(1))
                    .where(qPost.postSn.eq(postSn))
                    .execute();

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO setPostCmtReact(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn      = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long postCmtSn = Long.parseLong(params.get("postCmtSn").toString());
            String reactTyp  = params.get("reactTyp").toString();

            TblPostCmtReact existing =
                    tblPostCmtReactRepository.findByPostCmtSnAndUserSn(postCmtSn, userSn);

            if (existing == null) {
                // 처음 반응
                tblPostCmtReactRepository.save(TblPostCmtReact.builder()
                        .postCmtSn(postCmtSn)
                        .userSn(userSn)
                        .reactTyp(reactTyp)
                        .creatrSn(userSn)
                        .build());
            } else if (existing.getReactTyp().equals(reactTyp)) {
                // 같은 반응 재클릭 → 취소
                tblPostCmtReactRepository.delete(existing);
            } else {
                // 다른 반응으로 전환
                existing.setReactTyp(reactTyp);
            }

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO setPostDel(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            long postSn = Long.parseLong(params.get("postSn").toString());

            if(params.get("type").equals("COMM")){
                TblCommPost tblCommPost = tblCommPostRepository.findByCommPostSn(postSn);
                tblCommPost.setActvtnYn("N");
                tblCommPost.setMdfrSn(userSn);
                tblCommPostRepository.save(tblCommPost);
            }else{
                TblPost tblPost = tblPostRepository.findByPostSn(postSn);
                tblPost.setActvtnYn("N");
                tblPost.setMdfrSn(userSn);
                tblPostRepository.save(tblPost);
            }

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());

        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.DELETE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.DELETE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getPostCmtList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            Long userSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            JPAQueryFactory q = new JPAQueryFactory(em);

            long postSn = Long.parseLong(params.get("postSn").toString());
            int  size       = params.get("size")   != null ? Integer.parseInt(params.get("size").toString()) : 10;
            Long cursor     = params.get("cursor") != null ? Long.parseLong(params.get("cursor").toString()) : null;

            QTblPostCmt cmt     = QTblPostCmt.tblPostCmt;
            QTblUser        cmtUser = new QTblUser("cmtUser");

            // ── 1. 루트 댓글만 커서 페이지네이션 (commPostCmtSn ASC) ──────────
            var rootCondition = cmt.postSn.eq(postSn)
                    .and(cmt.stat.eq("ACTIVE"))
                    .and(cmt.actvtnYn.eq("Y"))
                    .and(cmt.prntCmtSn.isNull());
            if (cursor != null) rootCondition = rootCondition.and(cmt.postCmtSn.gt(cursor));

            List<com.querydsl.core.Tuple> rootTuples = q.select(cmt, cmtUser.name)
                    .from(cmt)
                    .join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn))
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

            List<Long> rootSns = rootTuples.stream()
                    .map(t -> t.get(cmt).getPostCmtSn())
                    .toList();

            // ── 2. 대댓글 배치 로드 ────────────────────────────────────────────
            List<com.querydsl.core.Tuple> replyTuples = q.select(cmt, cmtUser.name)
                    .from(cmt)
                    .join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn))
                    .where(cmt.prntCmtSn.in(rootSns)
                            .and(cmt.stat.eq("ACTIVE"))
                            .and(cmt.actvtnYn.eq("Y")))
                    .orderBy(cmt.postCmtSn.asc())
                    .fetch();

            // ── 3. 반응 수 / 내 반응 배치 조회 ───────────────────────────────
            List<Long> allSns = new ArrayList<>(rootSns);
            replyTuples.stream().map(t -> t.get(cmt).getPostCmtSn()).forEach(allSns::add);

            Map<Long, Map<String, Long>> reactionsMap = new java.util.HashMap<>();
            tblPostCmtReactRepository.countByCmtSns(allSns).forEach(row -> {
                long   cmtSn = ((Number) row[0]).longValue();
                String typ   = (String) row[1];
                long   cnt   = ((Number) row[2]).longValue();
                reactionsMap.computeIfAbsent(cmtSn, k -> new java.util.HashMap<>()).put(typ, cnt);
            });

            Map<Long, String> myReactMap = new java.util.HashMap<>();
            if (userSn != null) {
                tblPostCmtReactRepository.findByPostCmtSnInAndUserSn(allSns, userSn)
                        .forEach(r -> myReactMap.put(r.getPostCmtSn(), r.getReactTyp()));
            }

            // ── 4. 트리 조립 ──────────────────────────────────────────────────
            java.util.function.Function<com.querydsl.core.Tuple, CmtDTO> toDto = t -> {
                TblPostCmt c   = t.get(cmt);
                long           sn  = c.getPostCmtSn();
                return new CmtDTO(
                        sn, c.getUserSn(), t.get(cmtUser.name),
                        c.getCmtCntnt(), c.getLikeCnt(), c.getFrstCrtDt(), new ArrayList<>(),
                        reactionsMap.getOrDefault(sn, new java.util.HashMap<>()),
                        myReactMap.get(sn)
                );
            };

            Map<Long, CmtDTO> rootMap = new java.util.LinkedHashMap<>();
            rootTuples.forEach(t -> rootMap.put(t.get(cmt).getPostCmtSn(), toDto.apply(t)));
            replyTuples.forEach(t -> {
                Long prnt = t.get(cmt).getPrntCmtSn();
                CmtDTO parent = rootMap.get(prnt);
                if (parent != null) parent.getReplies().add(toDto.apply(t));
            });

            List<CmtDTO> list = new ArrayList<>(rootMap.values());

            // ── 5. nextCursor: 마지막 루트 댓글 SN (size 미달이면 null) ────────
            Long nextCursor = list.size() == size
                    ? rootSns.get(rootSns.size() - 1)
                    : null;

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

    public ResultVO getPostList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            Long          userSn       = params.get("userSn") != null ? Long.parseLong(params.get("userSn").toString()) : null;
            Long          viewerUserSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            LocalDateTime cursor       = params.get("cursor") != null ? LocalDateTime.parse(params.get("cursor").toString()) : null;
            int           size         = params.get("size")   != null ? Integer.parseInt(params.get("size").toString()) : 50;

            QTblPost qPost = QTblPost.tblPost;
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
            Map<Long, List<String>> tagMap = tagService.fetchTagMap(postSns, "post");

            // 내 좋아요 배치 조회 (TblPostLike는 likeTyp 없음 → actvtnYn "Y" 로 반환)
            java.util.Set<Long> likedSet = new java.util.HashSet<>();
            if (viewerUserSn != null) {
                tblPostLikeRepository.findByPostSnInAndUserSn(postSns, viewerUserSn)
                        .stream()
                        .filter(l -> "Y".equals(l.getActvtnYn()))
                        .forEach(l -> likedSet.add(l.getPostSn()));
            }

            // 내 저장 여부 배치 조회
            java.util.Set<Long> mySavedSet = new java.util.HashSet<>();
            if (viewerUserSn != null) {
                QTblPostSave qSave = QTblPostSave.tblPostSave;
                new JPAQueryFactory(em)
                        .select(qSave.postSn)
                        .from(qSave)
                        .where(qSave.userSn.eq(viewerUserSn)
                                .and(qSave.postKind.eq("POST"))
                                .and(qSave.postSn.in(postSns))
                                .and(qSave.actvtnYn.eq("Y")))
                        .fetch()
                        .forEach(mySavedSet::add);
            }

            List<Map<String, Object>> list = tuples.stream().map(t -> {
                TblPost p = t.get(qPost);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("postTyp",  "POST");
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
                map.put("mySaved",  mySavedSet.contains(p.getPostSn()));
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

    public ResultVO setPostSave(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long postSn = Long.parseLong(params.get("postSn").toString());
            String postKind = params.get("postKind").toString();
            boolean isSave = (boolean) params.get("isSave");
            if (isSave) {
                TblPostSave tblPostSave = new TblPostSave();
                tblPostSave.setUserSn(userSn);
                tblPostSave.setPostSn(postSn);
                tblPostSave.setPostKind(postKind);
                tblPostSave.setCreatrSn(userSn);
                tblPostSaveRepository.save(tblPostSave);
            }else{
                TblPostSave tblPostSave = tblPostSaveRepository.findByUserSnAndPostSnAndPostKind(userSn, postSn, postKind);
                if (tblPostSave != null) {
                    tblPostSaveRepository.delete(tblPostSave);
                }
            }

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getPostSaveList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            JPAQueryFactory  q     = new JPAQueryFactory(em);
            QTblUser         qUser = QTblUser.tblUser;
            QTblPostSave qTblPostSave = QTblPostSave.tblPostSave;
            QTblPost qPost = QTblPost.tblPost;
            QTblPostLike qTblPostLike = QTblPostLike.tblPostLike;
            QTblPostTag qPostTag = QTblPostTag.tblPostTag;

            QTblComm qComm = QTblComm.tblComm;
            QTblCommPost qCommPost = QTblCommPost.tblCommPost;
            QTblCommPostLike qCommPostLike = QTblCommPostLike.tblCommPostLike;

            List<PostSaveDTO> post =  q.select(
                        Projections.constructor(
                                PostSaveDTO.class,
                                qTblPostSave, qUser.userSn, qUser.loginId, qUser.name,
                                Expressions.constant(""), Expressions.constant(""), qPost.postTtl, qPost.postCntnt,
                                qPost.frstCrtDt, qPost.viewCnt, qPost.likeCnt, qPost.cmtCnt,
                                new CaseBuilder().when(qTblPostLike.isNotNull()).then(qTblPostLike.likeTyp)
                                        .otherwise(Expressions.nullExpression()),
                                Expressions.constant(true)
                        )
                    )
                    .from(qTblPostSave)
                    .join(qPost)
                    .on(qTblPostSave.postSn.eq(qPost.postSn))
                    .join(qUser)
                    .on(qPost.userSn.eq(qUser.userSn))
                    .leftJoin(qTblPostLike)
                    .on(qTblPostLike.postSn.eq(qPost.postSn).and(qTblPostLike.userSn.eq(userSn)))
                    .where(qTblPostSave.userSn.eq(userSn)
                            .and(qTblPostSave.actvtnYn.eq("Y"))
                            .and(qTblPostSave.postKind.eq("POST"))
                            .and(qPost.actvtnYn.eq("Y")))
                    .fetch();

            List<Long> postSns = post.stream().map(t -> t.getTblPostSave().getPostSn()).toList();
            Map<Long, List<String>> tagMap = tagService.fetchTagMap(postSns, "commPost");
            post.forEach(d -> d.setTagNms(tagMap.getOrDefault(d.getTblPostSave().getPostSn(), List.of())));

            List<PostSaveDTO> commPost =  q.select(
                        Projections.constructor(
                                PostSaveDTO.class,
                                qTblPostSave,
                                qUser.userSn,
                                qUser.loginId,
                                qUser.name,
                                qComm.commNm,
                                qComm.commDsplNm,
                                qCommPost.postTtl,
                                qCommPost.postCntnt,
                                qCommPost.frstCrtDt,
                                qCommPost.viewCnt,
                                qCommPost.likeCnt,
                                qCommPost.cmtCnt,
                                new CaseBuilder().when(qCommPostLike.isNotNull()).then(qCommPostLike.likeTyp)
                                                .otherwise(Expressions.nullExpression()),
                                Expressions.constant(true)
                        )
                    )
                    .from(qTblPostSave)
                    .join(qCommPost)
                    .on(qTblPostSave.postSn.eq(qCommPost.commPostSn))
                    .join(qComm)
                    .on(qCommPost.commSn.eq(qComm.commSn))
                    .join(qUser)
                    .on(qCommPost.userSn.eq(qUser.userSn))
                    .leftJoin(qCommPostLike)
                    .on(qCommPostLike.commPostSn.eq(qCommPost.commPostSn).and(qCommPostLike.userSn.eq(userSn)))
                    .where(qTblPostSave.userSn.eq(userSn)
                            .and(qTblPostSave.actvtnYn.eq("Y"))
                            .and(qTblPostSave.postKind.eq("COMM"))
                            .and(qCommPost.actvtnYn.eq("Y")))
                    .fetch();

            List<Long> commPostSns = commPost.stream().map(t -> t.getTblPostSave().getPostSn()).toList();
            Map<Long, List<String>> commPostTagMap = tagService.fetchTagMap(commPostSns, "commPost");
            commPost.forEach(d -> d.setTagNms(commPostTagMap.getOrDefault(d.getTblPostSave().getPostSn(), List.of())));

            List<PostSaveDTO> list = new ArrayList<>();
            list.addAll(post);
            list.addAll(commPost);

            Long nextCursor = list.size() == 50
                    ? list.get(list.size() - 1).getTblPostSave().getPostSn()
                    : null;

            resultVO.putResult("list", list);
            resultVO.putResult("nextCursor", nextCursor);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }
}
