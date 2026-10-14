package com.knowra.user.service;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.jwt.JwtAuthenticationFilter;
import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.security.RefreshCookie;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.cmm.messaging.MailService;
import com.knowra.cmm.messaging.SmsService;
import com.knowra.cmm.messaging.VerificationService;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.common.util.ComUtil;
import com.knowra.user.entity.TblUser;
import com.knowra.user.entity.TblUserLgnHstry;
import com.knowra.user.entity.TblUserStng;
import com.knowra.user.repository.TblUserLgnHstryRepository;
import com.knowra.user.repository.TblUserRepository;
import com.knowra.user.repository.TblUserStngRepository;
import com.knowra.user.sns.SnsAuthService;
import com.knowra.user.sns.SnsIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RestTemplate restTemplate = new RestTemplate();

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final UserService userService;
    private final TblUserRepository tblUserRepository;
    private final SnsAuthService snsAuthService;
    private final com.knowra.session.SessionService sessionService;
    private final TblUserStngRepository tblUserStngRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisApiService redisApiService;
    private final TblUserLgnHstryRepository tblUserLgnHstryRepository;
    private final TblComFileRepository tblComFileRepository;
    private final FileUtil fileUtil;
    private final RefreshCookie refreshCookie;
    private final VerificationService verificationService;
    private final MailService mailService;
    private final SmsService smsService;
    private final com.knowra.cmm.ratelimit.RateLimiter rateLimiter;
    private final com.knowra.user.suspension.SuspensionService suspensionService;
    private final com.knowra.user.dormant.DormantService dormantService;
    private final com.knowra.session.NewDeviceService newDeviceService;
    private final com.knowra.user.twofactor.TwoFactorService twoFactorService;
    private final com.knowra.user.sns.SnsLinkService snsLinks;

    /*
     * 로그인 실패 제한(15분 창). 세 겹인 이유:
     *  - 계정+IP 5회: 한 사람이 한 계정을 두드리는 경우. 성공하면 초기화.
     *  - IP 30회: 한 곳에서 여러 계정을 돌아가며 두드리는 경우.
     *  - 계정 50회: 여러 IP 로 한 계정을 두드리는 경우. 넉넉하게 둔 건 남이 일부러 틀려서
     *    주인을 잠가 버리는 걸 어렵게 하려는 것이다(주인은 보통 IP 가 달라 앞의 5회에 안 걸린다).
     */
    private static final long LOGIN_WINDOW_SEC = 900;
    private static final int LOGIN_FAIL_ACCT_IP = 5, LOGIN_FAIL_IP = 30, LOGIN_FAIL_ACCT = 50;

    private static final int REDIS_DB = JwtAuthenticationFilter.REDIS_DB;
    private static final int PW_RESET_TTL = 1800;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 비밀번호 재설정 링크가 가리키는 프론트 주소. 로컬은 vite, 운영은 환경변수. */
    @Value("${knowra.front.base-url:http://localhost:5111}")
    private String frontBaseUrl;

    public ResultVO login(Map<String, String> params, String clientIp, HttpServletResponse response) {
        return login(params, clientIp, response, false);
    }

    /**
     * ⚠️ {@code skipPassword} 는 서버 안에서만 정한다 — 소셜 가입 직후처럼 신원이 이미 다른 경로
     * (제공자 티켓)로 확인된 경우뿐이다. 예전엔 요청 본문의 {@code snsYn=Y} 로 비밀번호 검사를
     * 건너뛰어, 아무 loginId 에 snsYn 만 붙이면 그 계정으로 로그인됐다.
     */
    private ResultVO login(Map<String, String> params, String clientIp, HttpServletResponse response, boolean skipPassword) {
        ResultVO resultVO = new ResultVO();
        try {
            String loginId = params.get("loginId");

            if (!skipPassword) {
                String acct = loginId == null ? "" : loginId.trim().toLowerCase(java.util.Locale.ROOT);
                String kAcctIp = "login:fail:" + acct + ":" + clientIp, kIp = "login:fail-ip:" + clientIp, kAcct = "login:fail-acct:" + acct;
                String over = rateLimiter.isOver(kAcctIp, LOGIN_FAIL_ACCT_IP) ? kAcctIp
                        : rateLimiter.isOver(kIp, LOGIN_FAIL_IP) ? kIp
                        : rateLimiter.isOver(kAcct, LOGIN_FAIL_ACCT) ? kAcct : null;
                if (over != null) {
                    // 잠긴 동안은 비밀번호가 맞아도 들이지 않는다 — 맞는지 여부를 알려 주면 잠금이 무의미하다
                    resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                    resultVO.setResultMessage("로그인 시도가 너무 많아요. " + rateLimiter.minutesLeft(over) + "분 뒤에 다시 시도해 주세요.");
                    return resultVO;
                }
                try {
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(loginId, params.get("password"))
                    );
                } catch (BadCredentialsException e) {
                    rateLimiter.count(kAcctIp, LOGIN_WINDOW_SEC);
                    rateLimiter.count(kIp, LOGIN_WINDOW_SEC);
                    rateLimiter.count(kAcct, LOGIN_WINDOW_SEC);
                    throw e;
                }
                rateLimiter.reset(kAcctIp);
            }

            long userSn = userService.getUserSn(loginId);
            // 정지(status='S')된 계정은 비밀번호가 맞아도 들여보내지 않는다 — 관리자 콘솔이 정지한다
            tblUserRepository.findById(userSn).ifPresent(u -> {
                if (suspensionService.isSuspended(u)) throw new IllegalStateException("SUSPENDED|" + com.knowra.user.suspension.SuspensionService.message(u));
                if (!"Y".equals(u.getActvtnYn())) throw new IllegalStateException("DELETED");   // 탈퇴한 계정 — 예전엔 비밀번호로 다시 들어왔다
            });
            // 2단계 인증이 켜져 있으면 토큰 대신 확인표 — 인증 앱 코드까지 맞아야 /login/2fa 가 토큰을 준다
            if (twoFactorService.isEnabled(userSn)) return twoFactorRequired(resultVO, userSn);
            finishLogin(userSn, loginId, clientIp, response, resultVO);
        } catch (BadCredentialsException e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
        } catch (IllegalStateException e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            String m = String.valueOf(e.getMessage());
            resultVO.setResultMessage(m.startsWith("SUSPENDED|") ? m.substring("SUSPENDED|".length()) : "이메일 또는 비밀번호가 올바르지 않습니다.");
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 로그인 마무리 — 세션(기기 목록의 한 줄)·토큰·리프레시 쿠키·로그인 기록. 비밀번호·소셜·2단계가 같이 쓴다 */
    private void finishLogin(long userSn, String loginId, String clientIp, HttpServletResponse response, ResultVO resultVO) {
        String sid = sessionService.start(userSn);
        resultVO.putResult("accessToken", jwtProvider.generateAccessToken(userSn, loginId, sid));
        response.addHeader("Set-Cookie", refreshCookie.issue(jwtProvider.generateRefreshToken(userSn, loginId, sid))); // 본문엔 안 싣는다
        TblUserLgnHstry h = new TblUserLgnHstry();
        h.setUserSn(userSn);
        h.setLgnIp(clientIp);
        tblUserLgnHstryRepository.save(h);
        // 휴면이었으면 여기서 푼다 — 화면이 「다시 오셨네요」를 띄울 수 있게 알린다
        if (dormantService.wake(userSn)) resultVO.putResult("reactivated", true);
        // 처음 보는 기기면 알린다(비동기 — 메일이 로그인을 붙잡지 않게)
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String ua = attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra ? sra.getRequest().getHeader("User-Agent") : null;
        newDeviceService.onLogin(userSn, ua, clientIp, sid);
        resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
    }

    private ResultVO twoFactorRequired(ResultVO resultVO, long userSn) {
        resultVO.putResult("twoFactor", true);
        resultVO.putResult("ticket", twoFactorService.issueTicket(userSn));
        resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        return resultVO;
    }

    /** 2단계: params ticket, code(인증 앱 6자리 또는 복구 코드) */
    public ResultVO loginTwoFactor(Map<String, String> params, String clientIp, HttpServletResponse response) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = twoFactorService.completeLogin(params.get("ticket"), params.get("code"));
            TblUser u = tblUserRepository.findById(userSn).orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없어요."));
            // 확인표를 받은 뒤 5분 사이에 정지·탈퇴됐을 수 있다
            if (suspensionService.isSuspended(u)) throw new IllegalArgumentException(com.knowra.user.suspension.SuspensionService.message(u));
            if (!"Y".equals(u.getActvtnYn())) throw new IllegalArgumentException("탈퇴한 계정입니다.");
            finishLogin(userSn, u.getLoginId(), clientIp, response, resultVO);
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 소셜 로그인. params: provider(GOOGLE|KAKAO|NAVER|APPLE, 없으면 GOOGLE) + 제공자별 자격
     * (GOOGLE accessToken / KAKAO·NAVER code,state / APPLE idToken,userName).
     *
     * <p>계정 매칭: ① 제공자+ID ② (이메일이 있으면) loginId=이메일 — 예전 구글 가입은 ②로만
     * 찾았으므로 남겨 둔다. 찾으면 토큰 발급, 못 찾으면 가입 티켓과 미리 채울 정보를 준다.
     */
    public ResultVO snsLogin(Map<String, String> params, String clientIp, HttpServletResponse httpResponse) {
        ResultVO resultVO = new ResultVO();
        try {
            String provider = StringUtils.hasText(params.get("provider")) ? params.get("provider").toUpperCase() : SnsAuthService.GOOGLE;
            SnsIdentity id = snsAuthService.fetchIdentity(provider, params);

            // 연결 표(V33)가 단일 진실 — 한 계정에 여러 소셜이 붙는다
            Long linked = snsLinks.findUser(id.provider(), id.id());
            Optional<TblUser> tblUser = linked == null ? Optional.empty() : tblUserRepository.findById(linked);
            // 이메일로 찾는 건 "예전 구글 가입(제공자 ID 없이 loginId=이메일)"을 잇는 용도뿐이다. ⚠️ 좁게 건다:
            // 제공자가 확인한 이메일 + 그 계정이 소셜 계정 + 아직 제공자가 안 묶임 + 구글. 예전엔 아무 계정이나
            // loginId 가 같으면 들어가서, 남이 이메일 아이디를 먼저 만들어 두거나(비밀번호 계정) 확인 안 된
            // 이메일을 적은 다른 제공자 계정으로 그 사람 계정에 로그인할 수 있었다.
            if (tblUser.isEmpty() && id.emailVerified() && StringUtils.hasText(id.email()) && SnsAuthService.GOOGLE.equals(id.provider()))
                tblUser = tblUserRepository.findByLoginId(id.email())
                        .filter(u -> "Y".equals(u.getSnsYn()) && !StringUtils.hasText(u.getSnsId())
                                && (u.getSnsName() == null || SnsAuthService.GOOGLE.equals(u.getSnsName())));

            if (tblUser.isEmpty()) {
                resultVO.putResult("needsProfile", true);
                resultVO.putResult("ticket", snsAuthService.issueTicket(id));
                Map<String, Object> info = new java.util.LinkedHashMap<>();
                info.put("provider", id.provider()); info.put("email", id.email()); info.put("name", id.name()); info.put("picture", id.picture());
                resultVO.putResult("snsInfo", info);
                resultVO.putResult("googleInfo", info); // 예전 프론트 호환
            } else {
                TblUser u = tblUser.get();
                if (suspensionService.isSuspended(u)) {
                    resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                    resultVO.setResultMessage(com.knowra.user.suspension.SuspensionService.message(u));
                    return resultVO;
                }
                if (!"Y".equals(u.getActvtnYn())) {
                    resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                    resultVO.setResultMessage("탈퇴한 계정입니다.");
                    return resultVO;
                }
                // 이메일로만 찾은 예전 계정은 제공자 ID 를 이번에 묶어 둔다 — 다음부터 ①로 바로 찾는다.
                // 이메일로만 찾은 예전 계정은 이번에 연결해 둔다 — 다음부터 연결 표로 바로 찾는다.
                // (예전엔 tbl_user.SNS_ID 에 썼는데 updatable=false 라 조용히 저장되지 않았다)
                if (linked == null) snsLinks.link(u.getUserSn(), id);
                // 소셜 로그인도 2단계를 탄다 — 소셜 계정을 뺏기면 그대로 들어오면 안 된다
                if (twoFactorService.isEnabled(u.getUserSn())) return twoFactorRequired(resultVO, u.getUserSn());
                finishLogin(u.getUserSn(), u.getLoginId(), clientIp, httpResponse, resultVO);
                return resultVO;
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException | IllegalStateException e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 로그아웃 — 액세스 토큰과 리프레시 쿠키를 <b>둘 다</b> 남은 수명만큼 블록리스트에 올리고
     * 쿠키를 지운다. <b>멱등이다</b>: 토큰이 없거나 이미 만료됐어도 성공으로 답한다.
     * 로그아웃을 "실패"시킬 이유가 없다 — 프론트는 어차피 로컬 상태를 지운다.
     */
    public ResultVO logout(String authorizationHeader, HttpServletRequest request, HttpServletResponse response) {
        ResultVO resultVO = new ResultVO();
        try {
            if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
                revokeIfValid(authorizationHeader.substring(7));
            }
            String rt = refreshCookie.read(request);
            // 로그아웃한 기기는 기기 목록에서도 빠진다
            if (StringUtils.hasText(rt) && jwtProvider.isValid(rt)) {
                String sid = jwtProvider.extractSid(rt);
                if (sid != null) sessionService.revoke(jwtProvider.extractUserSn(rt), sid);
            }
            revokeIfValid(rt);
            response.addHeader("Set-Cookie", refreshCookie.clear());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    private void revokeIfValid(String token) {
        if (!StringUtils.hasText(token) || !jwtProvider.isValid(token)) return;
        long ttl = jwtProvider.getRemainingTtlSeconds(token);
        if (ttl > 0) redisApiService.addToBlocklist(REDIS_DB, token, ttl);
    }

    /**
     * 액세스 토큰 갱신. 리프레시 토큰은 <b>쿠키에서만</b> 읽는다.
     *
     * <p><b>회전한다</b>: 쓰인 리프레시 토큰은 즉시 폐기하고 새 것을 내려준다. 그래야
     * 한 번 새어 나간 리프레시 토큰을 공격자와 정상 유저가 동시에 쓰는 상황에서
     * 둘 중 하나가 바로 막힌다(둘 다 같은 토큰으로 갱신을 시도하면 두 번째가 블록리스트에 걸린다).
     */
    public ResultVO refresh(HttpServletRequest request, HttpServletResponse response) {
        ResultVO resultVO = new ResultVO();
        try {
            String refreshToken = refreshCookie.read(request);
            if (!StringUtils.hasText(refreshToken)
                    || !jwtProvider.isValid(refreshToken)
                    || !jwtProvider.isRefreshToken(refreshToken)) {   // 이미 쓴 토큰은 아래 재사용 감지로 간다
                response.addHeader("Set-Cookie", refreshCookie.clear());
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("유효하지 않은 토큰입니다.");
                return resultVO;
            }

            String loginId = jwtProvider.extractLoginId(refreshToken);
            long userSn = jwtProvider.extractUserSn(refreshToken);

            // 정지된 계정은 갱신으로도 살아나면 안 된다 — 로그인만 막으면 리프레시 쿠키로 7일을 더 쓴다.
            if (tblUserRepository.findById(userSn).map(u -> suspensionService.isSuspended(u) || !"Y".equals(u.getActvtnYn())).orElse(true)) {   // 정지·탈퇴
                revokeIfValid(refreshToken);
                response.addHeader("Set-Cookie", refreshCookie.clear());
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("이용이 정지된 계정입니다.");
                return resultVO;
            }

            // 세션: 토큰의 sid 가 살아 있어야 갱신된다(다른 기기에서 「로그아웃」 하면 여기서 끊긴다).
            // 세션 도입 전 토큰(sid 없음)은 이번 갱신에서 새 세션을 받아 옮겨 탄다.
            String sid = jwtProvider.extractSid(refreshToken);
            if (sid == null) sid = sessionService.start(userSn);
            else if (!sessionService.touch(sid, userSn)) {
                revokeIfValid(refreshToken);
                response.addHeader("Set-Cookie", refreshCookie.clear());
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("이 기기에서 로그아웃됐어요. 다시 로그인해 주세요.");
                return resultVO;
            }

            // 이 리프레시 토큰을 "썼다"고 원자적으로 표시한다. 이미 쓰인 토큰이 다시 오면:
            //  - 15초 안: 여러 탭이 동시에 갱신한 것 — 거절만 한다(브라우저엔 먼저 끝난 쪽의 새 쿠키가 있다).
            //  - 그 뒤: 누가 옛 토큰을 들고 있다(탈취) — 이 세션을 통째로 끊는다. 예전엔 거절만 해서
            //    먼저 갱신한 쪽(도둑일 수도)이 계속 살아 있었고, 동시에 두 번 보내면 둘 다 통과해 갈래가 생겼다.
            String usedKey = "rt:used:" + com.knowra.cmm.errlog.ErrorCollector.sha256Public(refreshToken);
            if (!redisApiService.setIfAbsent(REDIS_DB, usedKey, String.valueOf(System.currentTimeMillis()), 8 * 24 * 3600)) {
                Object at = redisApiService.getRedis(REDIS_DB, usedKey);
                long since = at == null ? Long.MAX_VALUE : System.currentTimeMillis() - Long.parseLong(at.toString());
                if (since > 15_000) sessionService.revoke(userSn, sid);
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage(since > 15_000 ? "보안을 위해 이 기기에서 로그아웃했어요. 다시 로그인해 주세요." : "이미 갱신됐어요.");
                return resultVO;
            }
            if (redisApiService.isBlocklisted(REDIS_DB, refreshToken)) {   // 로그아웃으로 폐기된 토큰
                response.addHeader("Set-Cookie", refreshCookie.clear());
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("유효하지 않은 토큰입니다.");
                return resultVO;
            }
            revokeIfValid(refreshToken);
            String newAccessToken = jwtProvider.generateAccessToken(userSn, loginId, sid);
            String newRefreshToken = jwtProvider.generateRefreshToken(userSn, loginId, sid);

            resultVO.putResult("accessToken", newAccessToken);
            response.addHeader("Set-Cookie", refreshCookie.issue(newRefreshToken));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO join(Map<String, Object> params, String clientIp, MultipartFile profileImage, HttpServletResponse response) {
        ResultVO resultVO = new ResultVO();

        try {
            String loginId = ComUtil.getStrValue(params.get("loginId"));
            String rawPassword = ComUtil.getStrValue(params.get("password"));
            String snsYn = ComUtil.getStrValue(params.get("snsYn"));
            String email = VerificationService.normalize(VerificationService.EMAIL, ComUtil.getStrValue(params.get("email")));
            String phone = VerificationService.normalize(VerificationService.PHONE, ComUtil.getStrValue(params.get("phone")));

            // 일반 가입은 이메일이나 휴대폰 중 하나는 코드로 확인돼 있어야 한다. 구글 가입은 구글이 확인한다.
            if (!"Y".equals(snsYn)) {
                boolean emailOk = !email.isEmpty() && verificationService.isVerified(VerificationService.EMAIL, email, ComUtil.getStrValue(params.get("emailTicket")));
                boolean phoneOk = !phone.isEmpty() && verificationService.isVerified(VerificationService.PHONE, phone, ComUtil.getStrValue(params.get("phoneTicket")));
                // 적은 연락처는 전부 확인된 것이어야 한다 — 하나만 확인하고 다른 칸에 남의 이메일·번호를 적으면
                // 그 주인이 「이미 가입된」 으로 막히고, 비밀번호 찾기 메일도 엉뚱한 계정 것으로 간다.
                if ((!emailOk && !phoneOk) || (!email.isEmpty() && !emailOk) || (!phone.isEmpty() && !phoneOk)) {
                    resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                    resultVO.setResultMessage("본인 확인이 끝나지 않았습니다. 인증 코드를 먼저 확인해주세요.");
                    return resultVO;
                }
                if (rawPassword.length() < 8) {
                    resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                    resultVO.setResultMessage("비밀번호는 8자 이상이어야 합니다.");
                    return resultVO;
                }
            }
            // 아이디 규칙(소셜 가입 아이디는 서버가 정한다). 이메일 가입은 화면이 이메일을 아이디로 쓰므로 허용하되,
            // ⚠️ 이번에 인증까지 마친 그 이메일과 같을 때만 — 남의 이메일을 아이디로 먼저 잡아 두는 길을 막는다.
            String ruleErr = null;
            if (!"Y".equals(snsYn)) {
                boolean ownEmailId = !email.isEmpty() && loginId.equalsIgnoreCase(email)
                        && verificationService.isVerified(VerificationService.EMAIL, email, ComUtil.getStrValue(params.get("emailTicket")));
                if (!ownEmailId) ruleErr = com.knowra.user.UserRules.loginIdError(loginId);
                if (ownEmailId) loginId = email;   // 정규화된(소문자) 주소로
            }
            if (ruleErr == null) ruleErr = com.knowra.user.UserRules.nickNameError(ComUtil.getStrValue(params.get("nickName")));
            if (ruleErr != null) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage(ruleErr);
                return resultVO;
            }
            if (tblUserRepository.findByLoginId(loginId).isPresent()) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("이미 사용 중인 아이디입니다.");
                return resultVO;
            }
            if (!email.isEmpty() && tblUserRepository.findByEmail(email).isPresent()) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("이미 가입된 이메일입니다.");
                return resultVO;
            }
            String nickName = ComUtil.getStrValue(params.get("nickName"));
            if (tblUserRepository.findByNickName(nickName).isPresent()) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("이미 사용 중인 닉네임입니다.");
                return resultVO;
            }

            TblUser tblUser = new TblUser();
            tblUser.setEmail(email.isEmpty() ? null : email);
            tblUser.setPhone(phone.isEmpty() ? null : phone);
            tblUser.setLoginId(loginId);
            tblUser.setPassword(passwordEncoder.encode(rawPassword));
            tblUser.setName(ComUtil.getStrValue(params.get("name")));
            tblUser.setNickName(ComUtil.getStrValue(params.get("nickName")));
            tblUser.setBio(ComUtil.getStrValue(params.get("bio")));
            tblUser.setInterest(ComUtil.getStrValue(params.get("interest")));

            SnsIdentity joinedSns = null;   // 저장 뒤 연결 표에 넣는다
            if(snsYn.equals("Y")){
                // 신원은 폼이 아니라 티켓(서버가 제공자에게 확인한 것)에서 — 폼 값을 믿으면 남의 이메일로 계정을 만들 수 있다.
                SnsIdentity id = snsAuthService.consumeTicket(ComUtil.getStrValue(params.get("snsTicket")));
                joinedSns = id;
                if (id == null) {
                    resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                    resultVO.setResultMessage("소셜 인증이 만료됐어요. 처음부터 다시 시도해 주세요.");
                    return resultVO;
                }
                if (snsLinks.findUser(id.provider(), id.id()) != null) {
                    resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                    resultVO.setResultMessage("이미 가입된 소셜 계정이에요. 로그인해 주세요.");
                    return resultVO;
                }
                // 확인된 이메일만 아이디·이메일로 쓴다 — 확인 안 된 이메일을 아이디로 박으면 그 주소 주인의 자리를 선점한다
                String verifiedEmail = id.emailVerified() && StringUtils.hasText(id.email()) ? id.email() : null;
                String snsLoginId = verifiedEmail != null ? verifiedEmail : id.provider().toLowerCase() + "_" + id.id();
                if (tblUserRepository.findByLoginId(snsLoginId).isPresent()) {
                    resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                    resultVO.setResultMessage("이미 가입된 이메일이에요. 로그인해 주세요.");
                    return resultVO;
                }
                loginId = snsLoginId;
                tblUser.setLoginId(snsLoginId);
                tblUser.setEmail(verifiedEmail);
                tblUser.setSnsYn("Y");
                tblUser.setSnsId(id.id());
                tblUser.setSnsName(id.provider());
                tblUser.setPassword("SNS 연동");
            }
            tblUserRepository.save(tblUser);
            if (joinedSns != null) snsLinks.link(tblUser.getUserSn(), joinedSns);

            UserService.profileImageSave(profileImage, tblUser, fileUtil, tblComFileRepository, tblUserRepository);


            TblUserStng tblUserStng = new TblUserStng();
            tblUserStng.setUserSn(tblUser.getUserSn());
            tblUserStng.setCreatrSn(tblUser.getUserSn());
            tblUserStngRepository.save(tblUserStng);

            if (!email.isEmpty()) verificationService.consume(VerificationService.EMAIL, email);
            if (!phone.isEmpty()) verificationService.consume(VerificationService.PHONE, phone);

            // 소셜 가입은 위에서 티켓으로 신원을 확인했으니 비밀번호 없이 들여보낸다
            resultVO = login(Map.of("loginId", loginId, "password", rawPassword), clientIp, response, "Y".equals(snsYn));
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 가입 전 본인 확인 (이메일 · 휴대폰)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * params: channel(email|phone), target. 이미 가입된 주소면 코드를 보내지 않고 바로 알려 준다 —
     * 가입하려는 사람 본인이 치는 칸이라 존재 여부를 숨길 이유가 없다(비밀번호 찾기는 다르다).
     */
    public ResultVO sendVerifyCode(Map<String, String> params) {
        ResultVO resultVO = new ResultVO();
        try {
            String channel = params.getOrDefault("channel", VerificationService.EMAIL);
            String target  = VerificationService.normalize(channel, params.get("target"));
            boolean isEmail = VerificationService.EMAIL.equals(channel);
            if (isEmail ? !target.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") : !target.matches("^01[0-9]{8,9}$")) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage(isEmail ? "올바른 이메일 주소가 아닙니다." : "올바른 휴대폰 번호가 아닙니다.");
                return resultVO;
            }
            boolean taken = isEmail
                    ? tblUserRepository.findByEmail(target).isPresent()
                    : tblUserRepository.findByPhone(target).isPresent();
            if (taken) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage(isEmail ? "이미 가입된 이메일입니다. 로그인해주세요." : "이미 가입된 휴대폰 번호입니다.");
                return resultVO;
            }
            String code = verificationService.issue(channel, target);
            if (code == null) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("잠시 후 다시 시도해주세요. (재발송은 1분에 한 번)");
                return resultVO;
            }
            if (isEmail) {
                mailService.send(target, "[Knowra] 인증 코드 " + code,
                        "Knowra 가입 인증 코드는 " + code + " 입니다.\n10분 안에 입력해주세요. 본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.");
            } else {
                smsService.send(target, "[Knowra] 인증 코드 " + code + " (10분 내 입력)");
            }
            if (verificationService.exposeCode()) resultVO.putResult("devCode", code);
            resultVO.putResult("expiresIn", 600);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage("인증 코드를 보내지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        return resultVO;
    }

    /** params: channel, target, code */
    public ResultVO checkVerifyCode(Map<String, String> params) {
        ResultVO resultVO = new ResultVO();
        String channel = params.getOrDefault("channel", VerificationService.EMAIL);
        String target  = VerificationService.normalize(channel, params.get("target"));
        String ticket = verificationService.check(channel, target, params.get("code"));
        if (ticket != null) {
            resultVO.putResult("ticket", ticket);   // 가입·이메일 변경 때 이걸 같이 보낸다(확인한 사람만 쓸 수 있게)
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } else {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage("인증 코드가 올바르지 않거나 만료됐습니다.");
        }
        return resultVO;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 비밀번호 재설정
    // ─────────────────────────────────────────────────────────────────────

    /**
     * params: email. <b>항상 성공으로 답한다</b> — 어떤 이메일이 가입돼 있는지 이 창구로
     * 알아낼 수 없어야 한다. 가입된 일반 계정이면 30분짜리 토큰 링크를 메일로 보낸다.
     */
    public ResultVO forgotPassword(Map<String, String> params) {
        ResultVO resultVO = new ResultVO();
        try {
            String email = VerificationService.normalize(VerificationService.EMAIL, params.get("email"));
            Optional<TblUser> user = tblUserRepository.findByEmail(email);
            if (user.isPresent() && !"Y".equals(user.get().getSnsYn())) {
                byte[] buf = new byte[32]; secureRandom.nextBytes(buf);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
                // 사람마다 살아 있는 링크는 하나 — 새로 보내면 예전 링크는 죽는다
                long sn = user.get().getUserSn();
                Object prev = redisApiService.getRedis(REDIS_DB, "pwreset:user:" + sn);
                if (prev != null) redisApiService.delRedis(REDIS_DB, "pwreset:" + prev);
                redisApiService.setRedis(REDIS_DB, "pwreset:" + token, String.valueOf(sn), PW_RESET_TTL);
                redisApiService.setRedis(REDIS_DB, "pwreset:user:" + sn, token, PW_RESET_TTL);
                String link = frontBaseUrl + "/reset-password?token=" + token;
                mailService.send(email, "[Knowra] 비밀번호 재설정",
                        "아래 링크에서 새 비밀번호를 설정할 수 있습니다. 링크는 30분 동안만 유효합니다.\n\n" + link
                        + "\n\n본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.");
                if (verificationService.exposeCode()) resultVO.putResult("devToken", token);
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage("메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        return resultVO;
    }

    /** params: token, newPassword. 토큰은 한 번 쓰면 버린다. */
    public ResultVO resetPassword(Map<String, String> params) {
        ResultVO resultVO = new ResultVO();
        try {
            String token = params.get("token");
            String newPw = params.get("newPassword");
            if (!StringUtils.hasText(token) || newPw == null || newPw.length() < 8) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("비밀번호는 8자 이상이어야 합니다.");
                return resultVO;
            }
            Object userSnRaw = redisApiService.getRedis(REDIS_DB, "pwreset:" + token);
            if (userSnRaw == null) {
                resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
                resultVO.setResultMessage("링크가 만료됐거나 올바르지 않습니다. 다시 요청해주세요.");
                return resultVO;
            }
            TblUser user = tblUserRepository.findById(Long.parseLong(userSnRaw.toString())).orElseThrow();
            user.setPassword(passwordEncoder.encode(newPw));
            tblUserRepository.save(user);
            redisApiService.delRedis(REDIS_DB, "pwreset:" + token);
            redisApiService.delRedis(REDIS_DB, "pwreset:user:" + user.getUserSn());
            sessionService.revokeOthers(user.getUserSn(), null);   // 비밀번호를 잊었거나 뺏겼다 — 모든 기기를 끊는다
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage("비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해주세요.");
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return resultVO;
    }
}
