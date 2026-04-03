package com.knowra.user.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.entity.QTblTag;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.entity.TblTag;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.common.service.TagService;
import com.knowra.common.util.ComUtil;
import com.knowra.community.entity.QTblComm;
import com.knowra.community.entity.QTblCommMbr;
import com.knowra.community.entity.QTblCommPost;
import com.knowra.community.entity.QTblCommPostLike;
import com.knowra.community.entity.QTblCommPostTag;
import com.knowra.post.entity.*;
import com.knowra.post.service.PostService;
import com.knowra.community.service.CommunityPostService;
import com.knowra.user.entity.*;
import com.knowra.user.repository.TblUserFlwrRepository;
import com.knowra.user.repository.TblUserRepository;
import com.knowra.user.repository.TblUserStngRepository;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final TblUserRepository tblUserRepository;
    private final TblUserFlwrRepository tblUserFlwrRepository;
    private final JwtProvider jwtProvider;
    private final PostService postService;
    private final CommunityPostService communityPostService;
    private final TagService tagService;
    private final FileUtil fileUtil;
    private final TblComFileRepository tblComFileRepository;
    private final TblUserStngRepository tblUserStngRepository;

    @PersistenceContext
    private EntityManager em;

    private final RedisApiService redisApiService;

    public Long getUserSn(String loginId) {
        return tblUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + loginId))
                .getUserSn();
    }

    public TblUser getUser(String loginId) {
        return tblUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + loginId));
    }

    public ResultVO getUserProfile(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            JPAQueryFactory q = new JPAQueryFactory(em);
            Long userSn = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            String loginId = params.get("loginId").toString();

            TblUser tblUser = tblUserRepository.findByLoginId(loginId).orElseThrow();
            QTblUserFlwr qFlwr = QTblUserFlwr.tblUserFlwr;

            if(userSn == null){
                resultVO.putResult("isFollowing", false);
            }else{
                TblUserFlwr usrFlwr = q.selectFrom(qFlwr)
                        .where(qFlwr.flwrUserSn.eq(userSn).and(qFlwr.flwngUserSn.eq(tblUser.getUserSn()))
                                .and(qFlwr.actvtnYn.eq("Y"))).fetchOne();
                resultVO.putResult("isFollowing", usrFlwr != null);
            }

            resultVO.putResult("userProfile", tblUserRepository.findByLoginId(loginId));
            resultVO.putResult("userSetting", tblUserStngRepository.findByUserSn(userSn));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }


    public ResultVO setUserInfo(Map<String, Object> params, MultipartFile profileImage, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            Long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            TblUser tblUser = tblUserRepository.findByUserSn(userSn);
            tblUser.setName(ComUtil.getStrValue(params.get("name")));
            tblUser.setBio(ComUtil.getStrValue(params.get("bio")));
            tblUser.setInterest(ComUtil.getStrValue(params.get("interest")));

            if(params.get("removeProfileImage") != null){
                boolean isDelete = fileUtil.deleteFile(new String[]{tblUser.getPfp().getStrgFileNm() + "." + tblUser.getPfp().getAtchFileExtnNm()}, tblUser.getPfp().getAtchFilePathNm());
                if(isDelete){
                    tblComFileRepository.delete(tblUser.getPfp());
                    tblUser.setPfp(null);
                    tblUserRepository.save(tblUser);
                }else{
                    throw new NullPointerException();
                }
            }

            profileImageSave(profileImage, tblUser, fileUtil, tblComFileRepository, tblUserRepository);

            boolean pfpChanged = params.get("removeProfileImage") != null || profileImage != null;
            if (pfpChanged) {
                String loginId = jwtProvider.extractLoginId(token.replace("Bearer ", ""));
                resultVO.putResult("accessToken",  jwtProvider.generateAccessToken(userSn, loginId));
                resultVO.putResult("refreshToken", jwtProvider.generateRefreshToken(userSn, loginId));
            }

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO setUserSetting(TblUserStng tblUserStng, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            Long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            tblUserStng.setMdfrSn(userSn);
            tblUserStngRepository.save(tblUserStng);

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    static void profileImageSave(MultipartFile profileImage, TblUser tblUser, FileUtil fileUtil, TblComFileRepository tblComFileRepository, TblUserRepository tblUserRepository) throws IOException {
        if (profileImage != null) {
            TblComFile proFile = fileUtil.devFileInf(
                    profileImage,
                    "/user/" + tblUser.getUserSn() + "/profile",
                    "user_" + tblUser.getUserSn()
            );
            proFile.setCreatrSn(tblUser.getUserSn());
            TblComFile pfp = tblComFileRepository.save(proFile);

            if(tblUser.getPfp() != null){
                tblComFileRepository.delete(tblUser.getPfp());
            }

            tblUser.setPfp(pfp);
            tblUserRepository.save(tblUser);
        }
    }

    public ResultVO getUserPostList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            if (params.get("loginId") == null) {
                resultVO.putResult("list",       List.of());
                resultVO.putResult("nextCursor", null);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }
            long userSn          = getUserSn(params.get("loginId").toString());
            Long viewerUserSn    = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            LocalDateTime cursor = params.get("cursor") != null
                    ? LocalDateTime.parse(params.get("cursor").toString()) : null;
            int size             = params.get("size") != null ? Integer.parseInt(params.get("size").toString()) : 20;

            // 두 소스에서 각각 size개씩 조회
            List<Map<String, Object>> commPosts = communityPostService.fetchCommPostListByUser(userSn, viewerUserSn, cursor, size);

            Map<String, Object> postParams = new java.util.HashMap<>();
            postParams.put("userSn", userSn);
            postParams.put("cursor", cursor != null ? cursor.toString() : null);
            postParams.put("size",   size);
            ResultVO postResult = postService.getPostList(postParams, token);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> posts = (List<Map<String, Object>>) postResult.getResult("list");

            // 합산 후 frstCrtDt 내림차순 정렬, 상위 size개만 반환
            List<Map<String, Object>> merged = new ArrayList<>();
            merged.addAll(commPosts);
            merged.addAll(posts != null ? posts : List.of());
            merged.sort((a, b) -> ((LocalDateTime) b.get("frstCrtDt")).compareTo((LocalDateTime) a.get("frstCrtDt")));

            List<Map<String, Object>> list = merged.size() > size ? merged.subList(0, size) : merged;
            LocalDateTime nextCursor = list.size() == size
                    ? (LocalDateTime) list.get(list.size() - 1).get("frstCrtDt") : null;

            resultVO.putResult("list",       list);
            resultVO.putResult("nextCursor", nextCursor != null ? nextCursor.toString() : null);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ── 팔로워 목록 (나를 팔로우하는 사람들) ───────────────────────────────
    public ResultVO getUserFollowerList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            Long myUserSn     = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            long targetUserSn = getUserSn(params.get("loginId").toString());

            QTblUserFlwr qFlwr     = QTblUserFlwr.tblUserFlwr;
            QTblUser     qFollower = new QTblUser("follower");
            QTblComFile  pfpFile   = new QTblComFile("pfpFile");

            List<com.querydsl.core.Tuple> tuples = new JPAQueryFactory(em)
                    .select(qFollower.userSn, qFollower.loginId, qFollower.name, pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                    .from(qFlwr)
                    .join(qFollower).on(qFlwr.flwrUserSn.eq(qFollower.userSn))
                    .leftJoin(qFollower.pfp, pfpFile)
                    .where(qFlwr.flwngUserSn.eq(targetUserSn).and(qFlwr.actvtnYn.eq("Y")))
                    .orderBy(qFlwr.frstCrtDt.desc())
                    .fetch();

            // 내가 팔로우 중인 유저 SN 집합 (isFollowing 표시용)
            java.util.Set<Long> myFollowings = getMyFollowingSnSet(myUserSn);

            List<Map<String, Object>> list = tuples.stream().map(t -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("userSn",      t.get(qFollower.userSn));
                map.put("loginId",     t.get(qFollower.loginId));
                map.put("name",        t.get(qFollower.name));
                map.put("isFollowing", myFollowings.contains(t.get(qFollower.userSn)));
                String pathNm = t.get(pfpFile.atchFilePathNm);
                map.put("pfpUrl", pathNm != null ? pathNm + "/" + t.get(pfpFile.strgFileNm) + "." + t.get(pfpFile.atchFileExtnNm) : null);
                return map;
            }).toList();

            resultVO.putResult("list", list);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ── 팔로잉 목록 (내가 팔로우하는 사람들) ───────────────────────────────
    public ResultVO getUserFollowingList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            Long myUserSn     = token != null ? jwtProvider.extractUserSn(token.replace("Bearer ", "")) : null;
            long targetUserSn = getUserSn(params.get("loginId").toString());

            QTblUserFlwr qFlwr     = QTblUserFlwr.tblUserFlwr;
            QTblUser     qFollowee = new QTblUser("followee");
            QTblComFile  pfpFile   = new QTblComFile("pfpFile");

            List<com.querydsl.core.Tuple> tuples = new JPAQueryFactory(em)
                    .select(qFollowee.userSn, qFollowee.loginId, qFollowee.name, pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
                    .from(qFlwr)
                    .join(qFollowee).on(qFlwr.flwngUserSn.eq(qFollowee.userSn))
                    .leftJoin(qFollowee.pfp, pfpFile)
                    .where(qFlwr.flwrUserSn.eq(targetUserSn).and(qFlwr.actvtnYn.eq("Y")))
                    .orderBy(qFlwr.frstCrtDt.desc())
                    .fetch();

            java.util.Set<Long> myFollowings = getMyFollowingSnSet(myUserSn);

            List<Map<String, Object>> list = tuples.stream().map(t -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("userSn",      t.get(qFollowee.userSn));
                map.put("loginId",     t.get(qFollowee.loginId));
                map.put("name",        t.get(qFollowee.name));
                map.put("isFollowing", myFollowings.contains(t.get(qFollowee.userSn)));
                String pathNm = t.get(pfpFile.atchFilePathNm);
                map.put("pfpUrl", pathNm != null ? pathNm + "/" + t.get(pfpFile.strgFileNm) + "." + t.get(pfpFile.atchFileExtnNm) : null);
                return map;
            }).toList();

            resultVO.putResult("list", list);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ── 팔로우 / 언팔로우 토글 ─────────────────────────────────────────────
    public ResultVO setFollow(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        try {
            long myUserSn     = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long targetUserSn = getUserSn(params.get("loginId").toString());

            if (myUserSn == targetUserSn) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("자기 자신을 팔로우할 수 없습니다.");
                return resultVO;
            }

            TblUserFlwr existing = tblUserFlwrRepository
                    .findByFlwrUserSnAndFlwngUserSn(myUserSn, targetUserSn)
                    .orElse(null);

            String newState;
            if (existing == null) {
                tblUserFlwrRepository.save(TblUserFlwr.builder()
                        .flwrUserSn(myUserSn)
                        .flwngUserSn(targetUserSn)
                        .actvtnYn("Y")
                        .creatrSn(myUserSn)
                        .build());
                newState = "FOLLOW";
            } else {
                existing.setActvtnYn("Y".equals(existing.getActvtnYn()) ? "N" : "Y");
                newState = "Y".equals(existing.getActvtnYn()) ? "FOLLOW" : "UNFOLLOW";
            }

            resultVO.putResult("state", newState);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }

    // 내가 팔로우 중인 userSn 집합 (isFollowing 일괄 체크용)
    private java.util.Set<Long> getMyFollowingSnSet(Long myUserSn) {
        if (myUserSn == null) return java.util.Set.of();
        QTblUserFlwr qFlwr = QTblUserFlwr.tblUserFlwr;
        return new java.util.HashSet<>(new JPAQueryFactory(em)
                .select(qFlwr.flwngUserSn)
                .from(qFlwr)
                .where(qFlwr.flwrUserSn.eq(myUserSn).and(qFlwr.actvtnYn.eq("Y")))
                .fetch());
    }

    public ResultVO changePassword(Map<String, Object> params, String authorization) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(authorization.replace("Bearer ", ""));
            TblUser tblUser = tblUserRepository.findById(userSn).orElseThrow();

            String currentPw = params.get("currentPw").toString();
            String newPw = params.get("newPw").toString();

            PasswordEncoder encoder = new BCryptPasswordEncoder();
            if(!encoder.matches(currentPw, tblUser.getPassword())){
                // 입력한 현재 비밀번호가 일치 하지 않을때
                resultVO.setResultCode(ResponseCode.CURRENT_PW_NOT_EQ.getCode());
                resultVO.setResultMessage(ResponseCode.CURRENT_PW_NOT_EQ.getMessage());
                return resultVO;
            }


            boolean ispw = encoder.matches(newPw, tblUser.getPassword());
            if(ispw){
                // 변경할 비밀번호가 현재 비밀번호와 일치할때
                resultVO.setResultCode(ResponseCode.NEW_PW_EQ.getCode());
                resultVO.setResultMessage(ResponseCode.NEW_PW_EQ.getMessage());
                return resultVO;
            }

            tblUser.setPassword(encoder.encode(newPw));
            tblUserRepository.save(tblUser);

            resultVO.setResultCode(ResponseCode.CHANGE_PW_SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.CHANGE_PW_SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO deleteAccount(Map<String, Object> params, String authorization) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(authorization.replace("Bearer ", ""));
            TblUser tblUser = tblUserRepository.findById(userSn).orElseThrow();

            String password = params.get("password").toString();

            PasswordEncoder encoder = new BCryptPasswordEncoder();
            if(!encoder.matches(password, tblUser.getPassword())){
                resultVO.setResultCode(ResponseCode.CURRENT_PW_NOT_EQ.getCode());
                resultVO.setResultMessage(ResponseCode.CURRENT_PW_NOT_EQ.getMessage());
                return resultVO;
            }
            tblUser.setActvtnYn("N");
            tblUserRepository.save(tblUser);

            resultVO.setResultCode(ResponseCode.CHANGE_PW_SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.CHANGE_PW_SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getMyTagList(String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            QTblUserTag qUserTag = QTblUserTag.tblUserTag;
            QTblTag qTag = QTblTag.tblTag;
            List<TblUserTagDTO> userTags = new JPAQueryFactory(em)
                    .select(
                        Projections.constructor(
                            TblUserTagDTO.class,
                            qTag.tagSn,
                            qTag.tagNm,
                            qUserTag.userTagSn,
                            qUserTag.useCount
                        )
                    ).from(qTag)
                    .join(qUserTag)
                    .on(qTag.tagSn.eq(qUserTag.tagSn))
                    .where(qUserTag.userSn.eq(userSn))
                    .fetch();

            resultVO.putResult("tags", userTags);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getMyTagPostList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn           = jwtProvider.extractUserSn(token.replace("Bearer ", ""));
            long tagSn            = Long.parseLong(params.get("tagSn").toString());
            LocalDateTime cursor  = params.get("cursor") != null ? LocalDateTime.parse(params.get("cursor").toString()) : null;
            int size              = params.get("size") != null ? Integer.parseInt(params.get("size").toString()) : 50;

            JPAQueryFactory q = new JPAQueryFactory(em);

            QTblUser qUser = QTblUser.tblUser;
            QTblTag qTag = QTblTag.tblTag;
            QTblPost qPost = QTblPost.tblPost;
            QTblPostTag qPostTag = QTblPostTag.tblPostTag;
            QTblPostLike qTblPostLike = QTblPostLike.tblPostLike;

            QTblPostSave qTblPostSave = QTblPostSave.tblPostSave;

            QTblCommPost qCommPost = QTblCommPost.tblCommPost;
            QTblComm qComm = QTblComm.tblComm;
            QTblCommPostTag qCommPostTag = QTblCommPostTag.tblCommPostTag;
            QTblCommPostLike qCommPostLike = QTblCommPostLike.tblCommPostLike;
            QTblCommMbr qMbr = QTblCommMbr.tblCommMbr;

            // 비공개 커뮤니티 필터: public이거나 내가 멤버인 경우만 노출
            com.querydsl.core.types.dsl.BooleanExpression visibilityCondition = qComm.prvcyStng.eq("public")
                .or(com.querydsl.jpa.JPAExpressions.selectOne()
                    .from(qMbr)
                    .where(qMbr.commSn.eq(qComm.commSn)
                        .and(qMbr.userSn.eq(userSn))
                        .and(qMbr.stat.eq("ACTIVE"))
                        .and(qMbr.actvtnYn.eq("Y")))
                    .exists());

            List<PostDTO> posts = q.select(
                Projections.constructor(
                    PostDTO.class,
                    Expressions.constant("POST"),
                    qPost.postTyp, qPost.postSn, qUser.userSn,
                    qUser.loginId, qUser.name, qPost.postTtl,
                    qPost.postCntnt, qPost.frstCrtDt,
                    qPost.viewCnt, qPost.likeCnt, qPost.cmtCnt,
                    new CaseBuilder().when(qTblPostLike.isNotNull()).then(qTblPostLike.likeTyp)
                        .otherwise(Expressions.nullExpression()),
                    new CaseBuilder().when(qTblPostSave.isNotNull()).then(true)
                        .otherwise(Expressions.nullExpression())
                )
            ).from(qPost)
            .join(qUser)
            .on(qPost.userSn.eq(qUser.userSn))
            .leftJoin(qTblPostLike)
            .on(qTblPostLike.postSn.eq(qPost.postSn).and(qTblPostLike.userSn.eq(userSn)))
            .leftJoin(qTblPostSave)
            .on(qPost.postSn.eq(qTblPostSave.postSn).and(qTblPostSave.userSn.eq(userSn)))
            .join(qPostTag)
            .on(qPost.postSn.eq(qPostTag.postSn))
            .join(qTag)
            .on(qPostTag.tagSn.eq(qTag.tagSn))
            .where(qTag.tagSn.eq(tagSn)
                .and(cursor != null ? qPost.frstCrtDt.lt(cursor) : null))
            .orderBy(qPost.frstCrtDt.desc())
            .limit(size)
            .groupBy(qPost.postSn).fetch();

            List<Long> postSns = posts.stream().map(PostDTO::getPostSn).toList();
            Map<Long, List<String>> tagMap = tagService.fetchTagMap(postSns, "post");
            posts.forEach(p -> p.setTagNms(tagMap.getOrDefault(p.getPostSn(), List.of())));

            List<PostDTO> commPosts = q.select(
                    Projections.constructor(
                            PostDTO.class,
                            Expressions.constant("COMM"),
                            qCommPost.postTyp, qComm.commSn, qComm.commNm,
                            qComm.commDsplNm, qCommPost.commPostSn, qUser.userSn,
                            qUser.loginId, qUser.name, qCommPost.postTtl, qCommPost.postCntnt,
                            qCommPost.frstCrtDt, qCommPost.viewCnt, qCommPost.likeCnt,
                            qCommPost.cmtCnt,
                            new CaseBuilder().when(qCommPostLike.isNotNull()).then(qCommPostLike.likeTyp)
                                    .otherwise(Expressions.nullExpression()),
                            new CaseBuilder().when(qTblPostSave.isNotNull()).then(true)
                                    .otherwise(Expressions.nullExpression())

                    )
            ).from(qCommPost)
            .join(qComm)
            .on(qCommPost.commSn.eq(qComm.commSn))
            .join(qUser)
            .on(qCommPost.userSn.eq(qUser.userSn))
            .leftJoin(qCommPostLike)
            .on(qCommPostLike.commPostSn.eq(qCommPost.commPostSn).and(qCommPostLike.userSn.eq(userSn)))
            .leftJoin(qTblPostSave)
            .on(qCommPost.commPostSn.eq(qTblPostSave.postSn).and(qTblPostSave.userSn.eq(userSn)))
            .join(qCommPostTag)
            .on(qCommPost.commPostSn.eq(qCommPostTag.commPostSn))
            .join(qTag)
            .on(qCommPostTag.tagSn.eq(qTag.tagSn))
            .where(qTag.tagSn.eq(tagSn)
                .and(visibilityCondition)
                .and(cursor != null ? qCommPost.frstCrtDt.lt(cursor) : null))
            .orderBy(qCommPost.frstCrtDt.desc())
            .limit(size)
            .groupBy(qCommPost.commPostSn).fetch();

            List<Long> commPostSns = commPosts.stream().map(PostDTO::getPostSn).toList();
            Map<Long, List<String>> commTagMap = tagService.fetchTagMap(commPostSns, "commPost");
            commPosts.forEach(p -> p.setTagNms(commTagMap.getOrDefault(p.getPostSn(), List.of())));

            // pfp 배치 조회
            List<Long> allUserSns = java.util.stream.Stream.concat(
                    posts.stream().map(PostDTO::getUserSn),
                    commPosts.stream().map(PostDTO::getUserSn)
            ).distinct().toList();
            if (!allUserSns.isEmpty()) {
                QTblUser    qU   = new QTblUser("pfpUser");
                QTblComFile pfpF = new QTblComFile("pfpF");
                Map<Long, String> pfpUrlMap = new JPAQueryFactory(em)
                        .select(qU.userSn, pfpF.atchFilePathNm, pfpF.strgFileNm, pfpF.atchFileExtnNm)
                        .from(qU)
                        .leftJoin(qU.pfp, pfpF)
                        .where(qU.userSn.in(allUserSns))
                        .fetch()
                        .stream()
                        .filter(t -> t.get(pfpF.atchFilePathNm) != null)
                        .collect(java.util.stream.Collectors.toMap(
                                t -> t.get(qU.userSn),
                                t -> t.get(pfpF.atchFilePathNm) + "/" + t.get(pfpF.strgFileNm) + "." + t.get(pfpF.atchFileExtnNm),
                                (a, b) -> a
                        ));
                posts.forEach(p -> p.setPfpUrl(pfpUrlMap.get(p.getUserSn())));
                commPosts.forEach(p -> p.setPfpUrl(pfpUrlMap.get(p.getUserSn())));
            }

            // 합산 → frstCrtDt 내림차순 → 상위 size개
            List<PostDTO> merged = new ArrayList<>();
            merged.addAll(posts);
            merged.addAll(commPosts);
            merged.sort((a, b) -> b.getFrstCrtDt().compareTo(a.getFrstCrtDt()));
            List<PostDTO> list = merged.size() > size ? merged.subList(0, size) : merged;

            LocalDateTime nextCursor = list.size() == size ? list.get(list.size() - 1).getFrstCrtDt() : null;

            resultVO.putResult("list", list);
            resultVO.putResult("nextCursor", nextCursor != null ? nextCursor.toString() : null);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }


}
