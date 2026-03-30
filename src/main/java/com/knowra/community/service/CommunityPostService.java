package com.knowra.community.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.common.entity.QTblTag;
import com.knowra.common.entity.TblTag;
import com.knowra.common.repository.TblTagRepository;
import com.knowra.common.service.TagService;
import com.knowra.post.entity.CmtDTO;
import com.knowra.post.entity.QTblPostSave;
import com.knowra.post.repository.TblPostSaveRepository;
import com.knowra.community.entity.*;
import com.knowra.community.repository.*;
import com.knowra.user.entity.QTblUser;
import com.knowra.user.entity.TblUserTag;
import org.modelmapper.ModelMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Transactional
public class CommunityPostService {
    private final ModelMapper modelMapper;
    private final TblCommPostRepository tblCommPostRepository;
    private final TblCommPostCmtRepository tblCommPostCmtRepository;
    private final TblCommPostCmtReactRepository tblCommPostCmtReactRepository;
    private final TblCommPostTagRepository tblCommPostTagRepository;
    private final TblCommPostLikeRepository tblCommPostLikeRepository;
    private final TblTagRepository tblTagRepository;
    private final TblPostSaveRepository tblPostSaveRepository;
    private final RedisApiService redisApiService;
    private final TagService tagService;
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
            tagService.setTag(tagNms, userSn, "commPost", tblCommPost.getCommPostSn());
            for (String tagNm : tagNms) {
                TblTag tblTag = tblTagRepository.findByTagNm(tagNm);
                if(tblTag != null){
                    tblTag.setUseCount(tblTag.getUseCount() + 1);
                    tblTagRepository.save(tblTag);
                }else{
                    tblTag = tblTagRepository.save(TblTag.builder().tagNm(tagNm).useCount(1).build());
                    tblTag.setCreatrSn(userSn);
                }

                TblCommPostTag tblCommPostTag = TblCommPostTag.builder()
                        .commPostSn(tblCommPost.getCommPostSn())
                        .tagSn(tblTag.getTagSn())
                        .build();
                tblCommPostTagRepository.save(tblCommPostTag);
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
                tuples = q.select(post, user.name, user.loginId)
                        .from(post)
                        .join(user).on(post.userSn.eq(user.userSn))
                        .where(condition)
                        .orderBy(post.likeCnt.desc(), post.commPostSn.desc())
                        .offset((long) page * 50)
                        .limit(50)
                        .fetch();
            } else if ("NOTICE".equals(listTyp)) {
                // 공지: postTyp = NOTICE, 커서 페이징
                condition = condition.and(post.postTyp.eq("NOTICE"));
                if (cursor != null) condition = condition.and(post.commPostSn.lt(cursor));
                tuples = q.select(post, user.name, user.loginId)
                        .from(post)
                        .join(user).on(post.userSn.eq(user.userSn))
                        .where(condition)
                        .orderBy(post.commPostSn.desc())
                        .limit(50)
                        .fetch();
            } else {
                // ALL / LATEST: 공지 항상 상단 고정 + 일반 게시글 커서 페이징
                if (cursor == null) {
                    // 첫 페이지: 공지 전체 먼저 조회
                    List<com.querydsl.core.Tuple> notices = q.select(post, user.name, user.loginId)
                            .from(post)
                            .join(user).on(post.userSn.eq(user.userSn))
                            .where(condition.and(post.postTyp.eq("NOTICE")))
                            .orderBy(post.commPostSn.desc())
                            .fetch();
                    // 일반 게시글: 50 - 공지수 만큼
                    int normalLimit = Math.max(50 - notices.size(), 0);
                    List<com.querydsl.core.Tuple> normals = q.select(post, user.name, user.loginId)
                            .from(post)
                            .join(user).on(post.userSn.eq(user.userSn))
                            .where(condition.and(post.postTyp.eq("NORMAL")))
                            .orderBy(post.commPostSn.desc())
                            .limit(normalLimit)
                            .fetch();
                    tuples = new ArrayList<>(notices);
                    tuples.addAll(normals);
                } else {
                    // 이후 페이지: 일반 게시글만 커서 페이징 (공지는 첫 페이지에 고정)
                    tuples = q.select(post, user.name, user.loginId)
                            .from(post)
                            .join(user).on(post.userSn.eq(user.userSn))
                            .where(condition.and(post.postTyp.eq("NORMAL"))
                                           .and(post.commPostSn.lt(cursor)))
                            .orderBy(post.commPostSn.desc())
                            .limit(50)
                            .fetch();
                }
            }

            List<Long> postSns = tuples.stream()
                    .map(t -> t.get(post).getCommPostSn())
                    .collect(java.util.stream.Collectors.toList());

            // 태그 일괄 조회
            Map<Long, List<String>> tagMap = tagService.fetchTagMap(postSns, "commPost");

            // 내 좋아요 일괄 조회 (postSn → likeTyp 맵)
            Map<Long, String> likeMap = new java.util.HashMap<>();
            if (!postSns.isEmpty() && userSn != null) {
                tblCommPostLikeRepository.findByCommPostSnInAndUserSn(postSns, userSn)
                        .forEach(like -> likeMap.put(like.getCommPostSn(), like.getLikeTyp()));
            }

            // 내 저장 여부 일괄 조회
            java.util.Set<Long> mySavedSet = new java.util.HashSet<>();
            if (!postSns.isEmpty() && userSn != null) {
                QTblPostSave qSave = QTblPostSave.tblPostSave;
                new JPAQueryFactory(em)
                        .select(qSave.postSn)
                        .from(qSave)
                        .where(qSave.userSn.eq(userSn)
                                .and(qSave.postKind.eq("COMM"))
                                .and(qSave.postSn.in(postSns))
                                .and(qSave.actvtnYn.eq("Y")))
                        .fetch()
                        .forEach(mySavedSet::add);
            }

            // DTO 조립
            List<CommunityPostDTO> list = new ArrayList<>();
            for (com.querydsl.core.Tuple t : tuples) {
                TblCommPost p = t.get(post);
                list.add(new CommunityPostDTO(
                        p.getCommPostSn(), p.getCommSn(), p.getUserSn(),
                        t.get(user.loginId), t.get(user.name), p.getPostTyp(), p.getPostTtl(), null, p.getFrstCrtDt(),
                        p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(),
                        tagMap.getOrDefault(p.getCommPostSn(), List.of()),
                        likeMap.get(p.getCommPostSn()),
                        mySavedSet.contains(p.getCommPostSn())
                ));
            }

            Long nextCursor = list.size() == 50
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

            // 게시글 + 작성자
            com.querydsl.core.Tuple postTuple = q.select(post, user.name, user.loginId)
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
            List<String> tagNms = tagService.fetchTagMap(List.of(commPostSn), "commPost").getOrDefault(commPostSn, List.of());

            // 내 좋아요 여부 (UP / DOWN / null)
            TblCommPostLike myLike = userSn != null
                    ? tblCommPostLikeRepository.findByCommPostSnAndUserSn(commPostSn, userSn)
                    : null;
            String myLikeTyp = myLike != null ? myLike.getLikeTyp() : null;

            boolean mySaved = userSn != null &&
                    tblPostSaveRepository.findByUserSnAndPostSnAndPostKind(userSn, commPostSn, "COMM") != null;

            resultVO.putResult("comm", community);
            resultVO.putResult("post", new CommunityPostDTO(
                    p.getCommPostSn(), p.getCommSn(), p.getUserSn(),
                    postTuple.get(user.loginId), postTuple.get(user.name), p.getPostTyp(), p.getPostTtl(), p.getPostCntnt(), p.getFrstCrtDt(),
                    p.getViewCnt(), p.getLikeCnt(), p.getCmtCnt(), tagNms, myLikeTyp, mySaved
            ));
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

    public ResultVO getCommPostCmtList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            Long userSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            JPAQueryFactory q = new JPAQueryFactory(em);

            long commPostSn = Long.parseLong(params.get("commPostSn").toString());
            int  size       = params.get("size")   != null ? Integer.parseInt(params.get("size").toString()) : 10;
            Long cursor     = params.get("cursor") != null ? Long.parseLong(params.get("cursor").toString()) : null;

            QTblCommPostCmt cmt     = QTblCommPostCmt.tblCommPostCmt;
            QTblUser        cmtUser = new QTblUser("cmtUser");

            // ── 1. 루트 댓글만 커서 페이지네이션 (commPostCmtSn ASC) ──────────
            var rootCondition = cmt.commPostSn.eq(commPostSn)
                    .and(cmt.stat.eq("ACTIVE"))
                    .and(cmt.actvtnYn.eq("Y"))
                    .and(cmt.prntCmtSn.isNull());
            if (cursor != null) rootCondition = rootCondition.and(cmt.commPostCmtSn.gt(cursor));

            List<com.querydsl.core.Tuple> rootTuples = q.select(cmt, cmtUser.name)
                    .from(cmt)
                    .join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn))
                    .where(rootCondition)
                    .orderBy(cmt.commPostCmtSn.asc())
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
                    .map(t -> t.get(cmt).getCommPostCmtSn())
                    .toList();

            // ── 2. 대댓글 배치 로드 ────────────────────────────────────────────
            List<com.querydsl.core.Tuple> replyTuples = q.select(cmt, cmtUser.name)
                    .from(cmt)
                    .join(cmtUser).on(cmt.userSn.eq(cmtUser.userSn))
                    .where(cmt.prntCmtSn.in(rootSns)
                            .and(cmt.stat.eq("ACTIVE"))
                            .and(cmt.actvtnYn.eq("Y")))
                    .orderBy(cmt.commPostCmtSn.asc())
                    .fetch();

            // ── 3. 반응 수 / 내 반응 배치 조회 ───────────────────────────────
            List<Long> allSns = new ArrayList<>(rootSns);
            replyTuples.stream().map(t -> t.get(cmt).getCommPostCmtSn()).forEach(allSns::add);

            Map<Long, Map<String, Long>> reactionsMap = new java.util.HashMap<>();
            tblCommPostCmtReactRepository.countByCmtSns(allSns).forEach(row -> {
                long   cmtSn = ((Number) row[0]).longValue();
                String typ   = (String) row[1];
                long   cnt   = ((Number) row[2]).longValue();
                reactionsMap.computeIfAbsent(cmtSn, k -> new java.util.HashMap<>()).put(typ, cnt);
            });

            Map<Long, String> myReactMap = new java.util.HashMap<>();
            if (userSn != null) {
                tblCommPostCmtReactRepository.findByCommPostCmtSnInAndUserSn(allSns, userSn)
                        .forEach(r -> myReactMap.put(r.getCommPostCmtSn(), r.getReactTyp()));
            }

            // ── 4. 트리 조립 ──────────────────────────────────────────────────
            java.util.function.Function<com.querydsl.core.Tuple, CmtDTO> toDto = t -> {
                TblCommPostCmt c   = t.get(cmt);
                long           sn  = c.getCommPostCmtSn();
                return new CmtDTO(
                        sn, c.getUserSn(), t.get(cmtUser.name),
                        c.getCmtCntnt(), c.getLikeCnt(), c.getFrstCrtDt(), new ArrayList<>(),
                        reactionsMap.getOrDefault(sn, new java.util.HashMap<>()),
                        myReactMap.get(sn)
                );
            };

            Map<Long, CmtDTO> rootMap = new java.util.LinkedHashMap<>();
            rootTuples.forEach(t -> rootMap.put(t.get(cmt).getCommPostCmtSn(), toDto.apply(t)));
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

    public ResultVO setCommPostLike(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            JPAQueryFactory q = new JPAQueryFactory(em);

            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long commPostSn = Long.parseLong(params.get("commPostSn").toString());
            String postLike = params.get("postLike").toString().toUpperCase();

            QTblCommPost qCommPost = QTblCommPost.tblCommPost;
            TblCommPostLike commPostLike = tblCommPostLikeRepository.findByCommPostSnAndUserSn(commPostSn, userSn);

            int delta;
            if (commPostLike == null) {
                // 처음 반응 → 추가
                delta = "UP".equals(postLike) ? 1 : -1;
                tblCommPostLikeRepository.save(TblCommPostLike.builder()
                        .userSn(userSn)
                        .commPostSn(commPostSn)
                        .likeTyp(postLike)
                        .creatrSn(userSn)
                        .build());
            } else if (commPostLike.getLikeTyp().equals(postLike)) {
                // 같은 반응 재클릭 → 취소
                delta = "UP".equals(postLike) ? -1 : 1;
                tblCommPostLikeRepository.delete(commPostLike);
            } else {
                // 반대 반응으로 전환 (DOWN→UP: +2, UP→DOWN: -2)
                delta = "UP".equals(postLike) ? 2 : -2;
                commPostLike.setLikeTyp(postLike);
                tblCommPostLikeRepository.save(commPostLike);
            }

            q.update(qCommPost)
                    .set(qCommPost.likeCnt, qCommPost.likeCnt.add(delta))
                    .where(qCommPost.commPostSn.eq(commPostSn))
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

    public ResultVO setCommPostCmtReact(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn      = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long commPostCmtSn = Long.parseLong(params.get("commPostCmtSn").toString());
            String reactTyp  = params.get("reactTyp").toString();

            TblCommPostCmtReact existing =
                    tblCommPostCmtReactRepository.findByCommPostCmtSnAndUserSn(commPostCmtSn, userSn);

            if (existing == null) {
                // 처음 반응
                tblCommPostCmtReactRepository.save(TblCommPostCmtReact.builder()
                        .commPostCmtSn(commPostCmtSn)
                        .userSn(userSn)
                        .reactTyp(reactTyp)
                        .creatrSn(userSn)
                        .build());
            } else if (existing.getReactTyp().equals(reactTyp)) {
                // 같은 반응 재클릭 → 취소
                tblCommPostCmtReactRepository.delete(existing);
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

    public ResultVO setCommPostCmt(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long commPostSn = Long.parseLong(params.get("commPostSn").toString());
            Long prntCmtSn = params.get("prntCmtSn") != null
                    ? Long.parseLong(params.get("prntCmtSn").toString()) : null;

            TblCommPostCmt cmt = TblCommPostCmt.builder()
                    .commPostSn(commPostSn)
                    .userSn(userSn)
                    .prntCmtSn(prntCmtSn)
                    .cmtCntnt(params.get("cmtCntnt").toString())
                    .creatrSn(userSn)
                    .build();
            tblCommPostCmtRepository.save(cmt);

            // 댓글수 +1
            JPAQueryFactory q = new JPAQueryFactory(em);
            QTblCommPost qCommPost = QTblCommPost.tblCommPost;
            q.update(qCommPost)
                    .set(qCommPost.cmtCnt, qCommPost.cmtCnt.add(1))
                    .where(qCommPost.commPostSn.eq(commPostSn))
                    .execute();

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * UserService 재사용 — userSn 기준 커뮤니티 게시글 목록 (날짜 커서 기반, 최신순)
     * 반환 필드: getCommPostList 기준(CommunityPostDTO) + commDsplNm
     */
    public List<Map<String, Object>> fetchCommPostListByUser(long userSn, Long viewerUserSn, java.time.LocalDateTime cursor, int size) {
        JPAQueryFactory  q     = new JPAQueryFactory(em);
        QTblCommPost     qPost = QTblCommPost.tblCommPost;
        QTblComm         qComm = QTblComm.tblComm;
        QTblUser         qUser = QTblUser.tblUser;
        QTblCommPostTag  qTag  = QTblCommPostTag.tblCommPostTag;
        QTblTag          qTbl  = QTblTag.tblTag;

        QTblCommMbr qMbr = QTblCommMbr.tblCommMbr;

        // 비공개 커뮤니티 필터: public 이거나 viewer가 해당 커뮤니티 멤버인 경우만 노출
        com.querydsl.core.types.dsl.BooleanExpression visibilityCondition = qComm.prvcyStng.eq("public");
        if (viewerUserSn != null) {
            visibilityCondition = visibilityCondition.or(
                com.querydsl.jpa.JPAExpressions.selectOne()
                    .from(qMbr)
                    .where(qMbr.commSn.eq(qComm.commSn)
                        .and(qMbr.userSn.eq(viewerUserSn))
                        .and(qMbr.stat.eq("ACTIVE"))
                        .and(qMbr.actvtnYn.eq("Y")))
                    .exists()
            );
        }

        var condition = qPost.userSn.eq(userSn)
                .and(qPost.stat.eq("ACTIVE"))
                .and(qPost.actvtnYn.eq("Y"))
                .and(visibilityCondition);
        if (cursor != null) condition = condition.and(qPost.frstCrtDt.lt(cursor));

        List<com.querydsl.core.Tuple> tuples = q
                .select(qPost, qUser.name, qComm.commNm, qComm.commDsplNm)
                .from(qPost)
                .join(qUser).on(qPost.userSn.eq(qUser.userSn))
                .join(qComm).on(qPost.commSn.eq(qComm.commSn))
                .where(condition)
                .orderBy(qPost.frstCrtDt.desc())
                .limit(size)
                .fetch();

        if (tuples.isEmpty()) return List.of();

        List<Long> postSns = tuples.stream().map(t -> t.get(qPost).getCommPostSn()).toList();

        // 태그 배치 조회
        Map<Long, List<String>> tagMap = tagService.fetchTagMap(postSns, "commPost");

        // 내 좋아요 배치 조회
        Map<Long, String> likeMap = new java.util.HashMap<>();
        if (viewerUserSn != null) {
            tblCommPostLikeRepository.findByCommPostSnInAndUserSn(postSns, viewerUserSn)
                    .forEach(l -> likeMap.put(l.getCommPostSn(), l.getLikeTyp()));
        }

        // 내 저장 여부 배치 조회
        java.util.Set<Long> mySavedSet = new java.util.HashSet<>();
        if (viewerUserSn != null) {
            QTblPostSave qSave = QTblPostSave.tblPostSave;
            new JPAQueryFactory(em)
                    .select(qSave.postSn)
                    .from(qSave)
                    .where(qSave.userSn.eq(viewerUserSn)
                            .and(qSave.postKind.eq("COMM"))
                            .and(qSave.postSn.in(postSns))
                            .and(qSave.actvtnYn.eq("Y")))
                    .fetch()
                    .forEach(mySavedSet::add);
        }

        return tuples.stream().map(t -> {
            TblCommPost p = t.get(qPost);
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("postType",   "COMM");
            map.put("commPostSn", p.getCommPostSn());
            map.put("commSn",     p.getCommSn());
            map.put("commNm",     t.get(qComm.commNm));
            map.put("commDsplNm", t.get(qComm.commDsplNm));
            map.put("userSn",     p.getUserSn());
            map.put("authorNm",   t.get(qUser.name));
            map.put("postTyp",    p.getPostTyp());
            map.put("postTtl",    p.getPostTtl());
            map.put("frstCrtDt",  p.getFrstCrtDt());
            map.put("viewCnt",    p.getViewCnt());
            map.put("likeCnt",    p.getLikeCnt());
            map.put("cmtCnt",     p.getCmtCnt());
            map.put("tagNms",     tagMap.getOrDefault(p.getCommPostSn(), List.of()));
            map.put("myLikeTyp",  likeMap.get(p.getCommPostSn()));
            map.put("mySaved",   mySavedSet.contains(p.getCommPostSn()));
            return (Map<String, Object>) map;
        }).toList();
    }
}
