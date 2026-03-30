package com.knowra.user.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.post.service.PostService;
import com.knowra.community.service.CommunityPostService;
import com.knowra.user.entity.QTblUser;
import com.knowra.user.entity.QTblUsrFlwr;
import com.knowra.user.entity.TblUser;
import com.knowra.user.entity.TblUsrFlwr;
import com.knowra.user.repository.TblUsrFlwrRepository;
import com.knowra.user.repository.TblUserRepository;
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
    private final TblUsrFlwrRepository tblUsrFlwrRepository;
    private final JwtProvider jwtProvider;
    private final PostService postService;
    private final CommunityPostService communityPostService;

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
            QTblUsrFlwr qFlwr = QTblUsrFlwr.tblUsrFlwr;

            if(userSn == null){
                resultVO.putResult("isFollowing", false);
            }else{
                TblUsrFlwr usrFlwr = q.selectFrom(qFlwr)
                        .where(qFlwr.flwrUserSn.eq(userSn).and(qFlwr.flwngUserSn.eq(tblUser.getUserSn()))
                                .and(qFlwr.actvtnYn.eq("Y"))).fetchOne();
                resultVO.putResult("isFollowing", usrFlwr != null);
            }

            resultVO.putResult("userProfile", tblUserRepository.findByLoginId(loginId));

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
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

            QTblUsrFlwr qFlwr     = QTblUsrFlwr.tblUsrFlwr;
            QTblUser    qFollower = new QTblUser("follower");

            List<com.querydsl.core.Tuple> tuples = new JPAQueryFactory(em)
                    .select(qFollower.userSn, qFollower.loginId, qFollower.name)
                    .from(qFlwr)
                    .join(qFollower).on(qFlwr.flwrUserSn.eq(qFollower.userSn))
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

            QTblUsrFlwr qFlwr    = QTblUsrFlwr.tblUsrFlwr;
            QTblUser    qFollowee = new QTblUser("followee");

            List<com.querydsl.core.Tuple> tuples = new JPAQueryFactory(em)
                    .select(qFollowee.userSn, qFollowee.loginId, qFollowee.name)
                    .from(qFlwr)
                    .join(qFollowee).on(qFlwr.flwngUserSn.eq(qFollowee.userSn))
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

            TblUsrFlwr existing = tblUsrFlwrRepository
                    .findByFlwrUserSnAndFlwngUserSn(myUserSn, targetUserSn)
                    .orElse(null);

            String newState;
            if (existing == null) {
                tblUsrFlwrRepository.save(TblUsrFlwr.builder()
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
        QTblUsrFlwr qFlwr = QTblUsrFlwr.tblUsrFlwr;
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
}
