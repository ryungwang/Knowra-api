package com.knowra.user.service;

import com.knowra.cmm.errlog.ErrorLog;
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
    private final com.knowra.session.SessionService sessionService;

    /** 로그인 폼이 보내는 건 아이디다. 닉네임으로 먼저 찾던 시절엔 닉네임 ≠ 아이디인 계정이 로그인 직후 여기서 죽었다. */
    public Long getUserSn(String loginId) {
        return tblUserRepository.findByLoginId(loginId)
                .or(() -> tblUserRepository.findByNickName(loginId))
                .orElseThrow(() -> new com.knowra.cmm.errlog.NotFoundException("없는 사용자예요: " + loginId))
                .getUserSn();
    }

    public TblUser getUser(String loginId) {
        return tblUserRepository.findByLoginId(loginId)
                .orElseThrow(() -> new com.knowra.cmm.errlog.NotFoundException("없는 사용자예요: " + loginId));
    }

    public ResultVO getUserProfile(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            JPAQueryFactory q = new JPAQueryFactory(em);
            Long userSn = me;
            String nickName = params.get("nickName").toString();

            TblUser tblUser = tblUserRepository.findByNickName(nickName).orElseThrow(() -> new com.knowra.cmm.errlog.NotFoundException("없는 사용자예요: " + nickName));
            QTblUserFlwr qFlwr = QTblUserFlwr.tblUserFlwr;

            if(userSn == null){
                resultVO.putResult("isFollowing", false);
                resultVO.putResult("isBlocked", false);
            }else{
                resultVO.putResult("isBlocked", tblUserBlockRepository.existsByUserSnAndBlockedSn(userSn, tblUser.getUserSn()));
                resultVO.putResult("isMuted", !em.createNativeQuery("SELECT 1 FROM knowra_user.tbl_user_mute WHERE user_sn = ?1 AND muted_sn = ?2")
                        .setParameter(1, userSn).setParameter(2, tblUser.getUserSn()).setMaxResults(1).getResultList().isEmpty());
                TblUserFlwr usrFlwr = q.selectFrom(qFlwr)
                        .where(qFlwr.flwrUserSn.eq(userSn).and(qFlwr.flwngUserSn.eq(tblUser.getUserSn()))
                                .and(qFlwr.actvtnYn.eq("Y"))).fetchOne();
                resultVO.putResult("isFollowing", usrFlwr != null);
            }

            // ⚠️ 엔티티를 통째로 내보내지 않는다 — 예전엔 비로그인 공개 API 가 비밀번호 해시·이메일·전화번호·
            // 로그인 아이디·SNS ID 까지 그대로 내줬다. 공개 필드만 고르고, 연락처는 본인에게만.
            Map<String, Object> profile = new java.util.LinkedHashMap<>();
            profile.put("userSn", tblUser.getUserSn());
            profile.put("nickName", tblUser.getNickName());
            profile.put("name", tblUser.getName());
            profile.put("bio", tblUser.getBio());
            profile.put("interest", tblUser.getInterest());
            profile.put("pfp", tblUser.getPfp());
            profile.put("joinYmd", tblUser.getJoinYmd());
            profile.put("frstCrtDt", tblUser.getFrstCrtDt());
            if (userSn != null && userSn == tblUser.getUserSn()) {
                profile.put("email", tblUser.getEmail());
                profile.put("phone", tblUser.getPhone());
                profile.put("loginId", tblUser.getLoginId());
                profile.put("snsYn", tblUser.getSnsYn());
                profile.put("snsName", tblUser.getSnsName());
                // 비밀번호가 있는 계정인가(소셜로만 가입하면 없다) — 탈퇴·이메일 변경 화면이 확인 방식을 고른다
                profile.put("hasPassword", tblUser.getPassword() != null && tblUser.getPassword().startsWith("$2"));
            }
            resultVO.putResult("userProfile", profile);
            resultVO.putResult("userSetting", tblUserStngRepository.findByUserSn(userSn));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            ErrorLog.report(e);
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
            // 닉네임은 프로필 주소이자 멘션 대상이다 — 규칙과 중복을 서버가 본다. 예전엔 검사가 없어 남의 닉네임으로
            // 바꾸면 그 사람의 프로필·DM·신고·멘션이 전부 깨졌다(같은 닉네임 두 줄 → 조회 오류).
            String nickName = ComUtil.getStrValue(params.get("nickName")).trim();
            String nickErr = com.knowra.user.UserRules.nickNameError(nickName);
            if (nickErr == null && !nickName.equals(tblUser.getNickName())
                    && tblUserRepository.findByNickName(nickName).isPresent()) nickErr = "이미 사용 중인 닉네임입니다.";
            if (nickErr != null) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage(nickErr);
                return resultVO;
            }
            tblUser.setName(ComUtil.getStrValue(params.get("name")));
            tblUser.setNickName(nickName);
            tblUser.setBio(ComUtil.getStrValue(params.get("bio")));
            tblUser.setInterest(ComUtil.getStrValue(params.get("interest")));

            if(params.get("removeProfileImage") != null && tblUser.getPfp() != null){
                boolean isDelete = fileUtil.deleteStored(tblUser.getPfp());
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
            // 같은 세션 id 로 — 예전엔 세션 없는 토큰을 내줘서, 이걸로 계속 새 토큰을 받으면 기기 로그아웃·정지로도 못 끊었다
            Object sid = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()
                    .getAttribute(com.knowra.cmm.jwt.JwtAuthenticationFilter.SID_ATTR, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            resultVO.putResult("accessToken", jwtProvider.generateAccessToken(userSn, tblUser.getLoginId(), (String) sid));

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    private static boolean isYn(Object v) { return "Y".equals(v) || "N".equals(v); }

    public ResultVO setUserSetting(java.util.Map<String, Object> b, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            Long userSn = me;

            // ⚠️ 받은 엔티티를 그대로 save 하지 않는다 — 예전엔 본문의 userStngSn·userSn 을 믿어서
            //    남의 설정 행 번호를 보내면 그 사람 설정을 덮어쓸 수 있었다. 내 행을 찾아 값만 옮긴다.
            TblUserStng org = tblUserStngRepository.findByUserSn(userSn);
            if (org == null) { org = new TblUserStng(); org.setUserSn(userSn); org.setCreatrSn(userSn); }
            // 보낸 칸만 바꾼다 — 헤더의 테마 전환은 themeTyp 하나만 보낸다
            Object theme = b.get("themeTyp");
            if (theme != null && java.util.Set.of("light", "dark", "system").contains(String.valueOf(theme))) org.setThemeTyp(String.valueOf(theme));
            if (isYn(b.get("cmtNtfctnYn")))  org.setCmtNtfctnYn(String.valueOf(b.get("cmtNtfctnYn")));
            if (isYn(b.get("flwrNtfctnYn"))) org.setFlwrNtfctnYn(String.valueOf(b.get("flwrNtfctnYn")));
            if (isYn(b.get("likeNtfctnYn"))) org.setLikeNtfctnYn(String.valueOf(b.get("likeNtfctnYn")));
            if (isYn(b.get("sysNtfctnYn")))  org.setSysNtfctnYn(String.valueOf(b.get("sysNtfctnYn")));
            if (isYn(b.get("msgNtfctnYn")))  org.setMsgNtfctnYn(String.valueOf(b.get("msgNtfctnYn")));
            if (isYn(b.get("digestYn")))     org.setDigestYn(String.valueOf(b.get("digestYn")));
            org.setMdfrSn(userSn);
            tblUserStngRepository.save(org);

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    static void profileImageSave(MultipartFile profileImage, TblUser tblUser, FileUtil fileUtil, TblComFileRepository tblComFileRepository, TblUserRepository tblUserRepository) throws IOException {
        if (profileImage != null) {
            TblComFile proFile = fileUtil.storeImage(profileImage, tblUser.getUserSn(), FileUtil.CAT_PROFILE, "user_" + tblUser.getUserSn());
            proFile.setCreatrSn(tblUser.getUserSn());
            TblComFile pfp = tblComFileRepository.save(proFile);

            if(tblUser.getPfp() != null){
                fileUtil.deleteStored(tblUser.getPfp());   // 바꾼 옛 사진 파일도 지운다 — 예전엔 DB 줄만 지워 파일이 공개 주소에 남았다
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
            ErrorLog.report(e);
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
                    .select(qFollower.userSn, qFollower.nickName, qFollower.name, pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm, pfpFile.fileKey)
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
                map.put("pfpUrl", com.knowra.cmm.storage.StorageUrls.of(pathNm, t.get(pfpFile.strgFileNm), t.get(pfpFile.atchFileExtnNm), t.get(pfpFile.fileKey)));
                return map;
            }).toList();

            resultVO.putResult("list", list);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
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
                    .select(qFollowee.userSn, qFollowee.nickName, qFollowee.name, pfpFile.atchFilePathNm, pfpFile.strgFileNm, pfpFile.atchFileExtnNm, pfpFile.fileKey)
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
                map.put("pfpUrl", com.knowra.cmm.storage.StorageUrls.of(pathNm, t.get(pfpFile.strgFileNm), t.get(pfpFile.atchFileExtnNm), t.get(pfpFile.fileKey)));
                return map;
            }).toList();

            resultVO.putResult("list", list);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
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
            // 차단한·차단당한 사이는 새로 팔로우할 수 없다(언팔로우는 된다) — 예전엔 차단당한 사람이 팔로우해 상대의 팔로워 목록에 떴다
            if ((existing == null || !"Y".equals(existing.getActvtnYn())) && (tblUserBlockRepository.existsByUserSnAndBlockedSn(myUserSn, targetUserSn)
                    || tblUserBlockRepository.existsByUserSnAndBlockedSn(targetUserSn, myUserSn))) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("팔로우할 수 없는 사용자예요.");
                return resultVO;
            }

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
            ErrorLog.report(e);
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

    /** 이 요청의 세션 id (JwtAuthenticationFilter 가 요청 속성에 넣어 둔다) */
    private static String currentSid() {
        Object v = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()
                .getAttribute(com.knowra.cmm.jwt.JwtAuthenticationFilter.SID_ATTR, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        return v == null ? null : v.toString();
    }

    public ResultVO changePassword(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = me;
            TblUser tblUser = tblUserRepository.findById(userSn).orElseThrow();

            String currentPw = params.get("currentPw").toString();
            String newPw = params.get("newPw").toString();
            if (newPw.length() < 8) {   // 재설정과 같은 기준 — 예전엔 변경에만 길이 제한이 없었다
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("비밀번호는 8자 이상이어야 합니다.");
                return resultVO;
            }

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
            // 비밀번호를 바꾸는 이유의 대부분은 "누가 들어온 것 같아서"다 — 지금 기기만 남기고 다른 세션을 끊는다.
            // 예전엔 끊지 않아서, 훔친 리프레시 쿠키가 비밀번호를 바꾼 뒤에도 계속 살았다.
            sessionService.revokeOthers(me, currentSid());

            resultVO.setResultCode(ResponseCode.CHANGE_PW_SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.CHANGE_PW_SUCCESS.getMessage());
        }catch (Exception e) {
            ErrorLog.report(e);
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
            ErrorLog.report(e);
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
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }


}
