package com.knowra.user.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.common.CommunityQueryHelper;
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
import com.knowra.post.entity.*;
import com.knowra.post.service.PostService;
import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.user.entity.*;
import com.knowra.user.event.UserFollowChangedEvent;
import com.knowra.user.repository.TblUserFlwrRepository;
import com.knowra.user.repository.TblUserRepository;
import com.knowra.user.repository.TblUserStngRepository;
import org.springframework.context.ApplicationEventPublisher;
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
    private final com.knowra.safety.repository.TblUserBlockRepository tblUserBlockRepository;
    private final TblUserFlwrRepository tblUserFlwrRepository;
    private final JwtProvider jwtProvider;
    private final PostService postService;
    private final TagService tagService;
    private final FileUtil fileUtil;
    private final TblComFileRepository tblComFileRepository;
    private final TblUserStngRepository tblUserStngRepository;
    private final ActionLogService actionLogService;
    private final InterestScoreService interestScoreService;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager em;

    private final RedisApiService redisApiService;

    /** 로그인 폼이 보내는 건 아이디다. 닉네임으로 먼저 찾던 시절엔 닉네임 ≠ 아이디인 계정이 로그인 직후 여기서 죽었다. */
    public Long getUserSn(String loginId) {
        return tblUserRepository.findByLoginId(loginId)
                .or(() -> tblUserRepository.findByNickName(loginId))
                .orElseThrow(() -> new IllegalStateException("User not found: " + loginId))
                .getUserSn();
    }

    public TblUser getUser(String loginId) {
        return tblUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + loginId));
    }

    public ResultVO getUserProfile(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            JPAQueryFactory q = new JPAQueryFactory(em);
            Long userSn = me;
            String nickName = params.get("nickName").toString();

            TblUser tblUser = tblUserRepository.findByNickName(nickName).orElseThrow();
            QTblUserFlwr qFlwr = QTblUserFlwr.tblUserFlwr;

            if(userSn == null){
                resultVO.putResult("isFollowing", false);
                resultVO.putResult("isBlocked", false);
            }else{
                resultVO.putResult("isBlocked", tblUserBlockRepository.existsByUserSnAndBlockedSn(userSn, tblUser.getUserSn()));
                TblUserFlwr usrFlwr = q.selectFrom(qFlwr)
                        .where(qFlwr.flwrUserSn.eq(userSn).and(qFlwr.flwngUserSn.eq(tblUser.getUserSn()))
                                .and(qFlwr.actvtnYn.eq("Y"))).fetchOne();
                resultVO.putResult("isFollowing", usrFlwr != null);
            }

            resultVO.putResult("userProfile", tblUserRepository.findByNickName(nickName));
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


    public ResultVO setUserInfo(Map<String, Object> params, MultipartFile profileImage, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            Long userSn = me;

            TblUser tblUser = tblUserRepository.findByUserSn(userSn);
            tblUser.setName(ComUtil.getStrValue(params.get("name")));
            tblUser.setNickName(ComUtil.getStrValue(params.get("nickName")));
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

            // 토큰에 nickName 이 들어 있어, 프로필을 고쳤으면 액세스 토큰을 새로 내려준다.
            // 리프레시 토큰은 쿠키에 그대로 있으니 건드리지 않는다. 예전엔 프로필 사진 URL 이
            // 토큰에 박혀 있어 사진만 바꿔도 토큰 둘을 다 재발급했다 — 그 클레임은 뺐다.
            resultVO.putResult("accessToken", jwtProvider.generateAccessToken(userSn, tblUser.getLoginId()));

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO setUserSetting(TblUserStng tblUserStng, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            Long userSn = me;

            if(tblUserStng.getUserStngSn() == null){
                /** 헤더에서 테마 변견시 */
                TblUserStng orgUserStng = tblUserStngRepository.findByUserSn(userSn);
                orgUserStng.setThemeTyp(tblUserStng.getThemeTyp());
                orgUserStng.setMdfrSn(userSn);
                tblUserStngRepository.save(orgUserStng);
            }else{
                tblUserStng.setMdfrSn(userSn);
                tblUserStngRepository.save(tblUserStng);
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

    public ResultVO getUserPostList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            if (params.get("nickName") == null) {
                resultVO.putResult("list",       List.of());
                resultVO.putResult("nextCursor", null);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
                return resultVO;
            }
            long userSn          = getUserSn(params.get("nickName").toString());
            Long viewerUserSn = me;
            LocalDateTime cursor = params.get("cursor") != null
                    ? LocalDateTime.parse(params.get("cursor").toString()) : null;
            int size             = params.get("size") != null ? Integer.parseInt(params.get("size").toString()) : 20;

            // 전체 공개 글 + viewer 가 볼 수 있는 커뮤니티 글을 한 쿼리로 (예전엔 두 소스를 합쳐 정렬했다)
            List<PostDTO> list = postService.fetchPostsByUser(userSn, viewerUserSn, cursor, size);
            LocalDateTime nextCursor = list.size() == size ? list.get(list.size() - 1).getFrstCrtDt() : null;

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
    public ResultVO getUserFollowerList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            Long myUserSn = me;
            long targetUserSn = getUserSn(params.get("nickName").toString());

            QTblUserFlwr qFlwr     = QTblUserFlwr.tblUserFlwr;
            QTblUser     qFollower = new QTblUser("follower");
            QTblComFile  pfpFile   = new QTblComFile("pfpFile");

            List<com.querydsl.core.Tuple> tuples = new JPAQueryFactory(em)
                    .select(qFollower.userSn, qFollower.nickName, qFollower.name, pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
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
                map.put("nickName",     t.get(qFollower.nickName));
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
    public ResultVO getUserFollowingList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            Long myUserSn = me;
            long targetUserSn = getUserSn(params.get("nickName").toString());

            QTblUserFlwr qFlwr     = QTblUserFlwr.tblUserFlwr;
            QTblUser     qFollowee = new QTblUser("followee");
            QTblComFile  pfpFile   = new QTblComFile("pfpFile");

            List<com.querydsl.core.Tuple> tuples = new JPAQueryFactory(em)
                    .select(qFollowee.userSn, qFollowee.nickName, qFollowee.name, pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm)
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
                map.put("nickName",     t.get(qFollowee.nickName));
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
    public ResultVO setFollow(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long myUserSn = me;
            long targetUserSn = getUserSn(params.get("nickName").toString());

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

            if ("FOLLOW".equals(newState)) {
                actionLogService.log(myUserSn, TblUserActionLog.TARGET_USER, targetUserSn, TblUserActionLog.ACTION_FOLLOW);
                interestScoreService.update(myUserSn, TblUserActionLog.TARGET_USER, targetUserSn, 6);
            } else {
                interestScoreService.update(myUserSn, TblUserActionLog.TARGET_USER, targetUserSn, -6);
            }
            eventPublisher.publishEvent(new UserFollowChangedEvent(myUserSn, targetUserSn, "FOLLOW".equals(newState)));
            if ("FOLLOW".equals(newState)) {
                eventPublisher.publishEvent(new NotifTriggerEvent(targetUserSn, myUserSn, "FOLLOW", null, null));
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

    public ResultVO changePassword(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = me;
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

    public ResultVO deleteAccount(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = me;
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

    public ResultVO getMyTagList(Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = me;

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

    public ResultVO getMyTagPostList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long          userSn = me;
            long          tagSn  = Long.parseLong(params.get("tagSn").toString());
            LocalDateTime cursor = params.get("cursor") != null ? LocalDateTime.parse(params.get("cursor").toString()) : null;
            int           size   = params.get("size")   != null ? Integer.parseInt(params.get("size").toString()) : 50;

            List<PostDTO> list = postService.fetchPostsByTag(tagSn, userSn, cursor, size);
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
