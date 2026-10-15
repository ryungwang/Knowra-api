package com.knowra.account;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.messaging.MailService;
import com.knowra.cmm.messaging.VerificationService;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.session.SessionService;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 계정 — 로그인한 기기와 이메일 바꾸기. 전부 로그인 필수.
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final SessionService sessions;
    private final JwtProvider jwt;
    private final TblUserRepository users;
    private final VerificationService verification;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final com.knowra.user.repository.TblUserStngRepository stngs;
    private final DataExportService dataExport;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    private final com.knowra.user.twofactor.TwoFactorService twoFactor;

    // ── 2단계 인증 ── 실패 메시지(코드 틀림 등)는 그대로 화면에 띄운다
    @PostMapping("/2fa/status")
    public ResultVO twoFactorStatus(@CurrentUser Long me) {
        ResultVO r = new ResultVO();
        twoFactor.status(me).forEach(r::putResult);
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    @PostMapping("/2fa/setup")
    public ResultVO twoFactorSetup(@CurrentUser Long me) {
        return attempt(() -> twoFactor.setup(me));
    }

    @PostMapping("/2fa/enable")
    public ResultVO twoFactorEnable(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return attempt(() -> Map.of("recoveryCodes", twoFactor.enable(me, String.valueOf(b.get("code")))));
    }

    @PostMapping("/2fa/disable")
    public ResultVO twoFactorDisable(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return attempt(() -> { twoFactor.disable(me, String.valueOf(b.get("code"))); return Map.of("enabled", false); });
    }

    @PostMapping("/2fa/recovery")
    public ResultVO twoFactorRecovery(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return attempt(() -> Map.of("recoveryCodes", twoFactor.regenerateRecovery(me, String.valueOf(b.get("code")))));
    }

    private ResultVO attempt(java.util.function.Supplier<Map<String, ?>> body) {
        ResultVO r = new ResultVO();
        try {
            body.get().forEach(r::putResult);
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            com.knowra.cmm.errlog.ErrorLog.report(e);
            r.setResultCode(ResponseCode.UPDATE_ERROR.getCode());
            r.setResultMessage(ResponseCode.UPDATE_ERROR.getMessage());
        }
        return r;
    }

    private final com.knowra.user.sns.SnsLinkService snsLinks;
    private final com.knowra.user.sns.SnsAuthService snsAuth;

    // ── 소셜 계정 연결 ── 연결은 제공자 인증을 다녀온 값(code·accessToken·idToken)으로만 — 폼 값을 믿지 않는다
    @PostMapping("/sns")
    public ResultVO snsList(@CurrentUser Long me) {
        ResultVO r = new ResultVO();
        r.putResult("linked", snsLinks.list(me));
        r.putResult("hasPassword", users.findById(me).map(u -> u.getPassword() != null && u.getPassword().startsWith("$2")).orElse(false));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: provider + 제공자별 자격(GOOGLE accessToken / KAKAO·NAVER code,state / APPLE idToken) */
    @PostMapping("/sns/link")
    public ResultVO snsLink(@RequestBody Map<String, String> b, @CurrentUser Long me) {
        return attempt(() -> {
            String provider = b.getOrDefault("provider", "").toUpperCase();
            snsLinks.link(me, snsAuth.fetchIdentity(provider, b));
            return Map.of("linked", snsLinks.list(me));
        });
    }

    /** body: provider */
    @PostMapping("/sns/unlink")
    public ResultVO snsUnlink(@RequestBody Map<String, String> b, @CurrentUser Long me) {
        return attempt(() -> {
            boolean hasPw = users.findById(me).map(u -> u.getPassword() != null && u.getPassword().startsWith("$2")).orElse(false);
            snsLinks.unlink(me, String.valueOf(b.get("provider")).toUpperCase(), hasPw);
            return Map.of("linked", snsLinks.list(me));
        });
    }

    /** 내 데이터 내려받기 — JSON 파일 한 개. 비싼 조회라 횟수 제한(RateLimitInterceptor) */
    @PostMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export(@CurrentUser Long me) throws Exception {
        byte[] body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(dataExport.export(me));
        String name = "knowra-data-" + java.time.LocalDate.now() + ".json";
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .header("Cache-Control", "no-store")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body);
    }

    /** 지금 요청을 보낸 토큰의 세션 id — 목록에서 「이 기기」를 표시하고, 「다른 기기 모두」에서 남긴다. */
    private String currentSid(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return null;
        try { return jwt.extractSid(h.substring(7)); } catch (Exception e) { return null; }
    }

    @PostMapping("/sessions")
    public ResultVO sessions(@CurrentUser Long me, HttpServletRequest req) {
        return ok("list", sessions.list(me, currentSid(req)));
    }

    /** body: sid — 그 기기 로그아웃. 지금 기기면 프론트가 로그아웃 처리까지 한다. */
    @PostMapping("/sessions/revoke")
    public ResultVO revoke(@RequestBody Map<String, Object> b, @CurrentUser Long me, HttpServletRequest req) {
        String sid = String.valueOf(b.get("sid"));
        boolean done = sessions.revoke(me, sid);
        ResultVO r = ok("revoked", done);
        r.putResult("current", sid.equals(currentSid(req)));
        return r;
    }

    @PostMapping("/sessions/revokeOthers")
    public ResultVO revokeOthers(@CurrentUser Long me, HttpServletRequest req) {
        String keep = currentSid(req);
        if (keep == null) return fail("이 기기를 알아볼 수 없어요. 다시 로그인한 뒤 해 주세요.");
        return ok("count", sessions.revokeOthers(me, keep));
    }

    /**
     * 이메일 바꾸기. 순서: (프론트) /api/auth/verify/send → /verify/check 로 새 이메일을 인증한 뒤 여기.
     * body: email, password(일반 가입 계정만)
     *
     * <p>이메일로 로그인하는 계정(loginId == 옛 이메일)은 로그인 아이디도 같이 바꾼다 — 안 바꾸면
     * 옛 주소로 로그인하게 된다. 옛 주소로는 "바뀌었다"는 안내 메일을 보낸다(본인이 아니면 알아차리게).
     */
    @PostMapping("/changeEmail")
    @Transactional
    public ResultVO changeEmail(@RequestBody Map<String, Object> b, @CurrentUser Long me, HttpServletRequest req) {
        String email = VerificationService.normalize(VerificationService.EMAIL, String.valueOf(b.getOrDefault("email", "")));
        String password = b.get("password") == null ? "" : String.valueOf(b.get("password"));
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return fail("이메일 주소를 확인해 주세요.");
        TblUser u = users.findById(me).orElse(null);
        if (u == null) return fail("계정을 찾을 수 없어요.");
        String old = u.getEmail();
        if (email.equalsIgnoreCase(old == null ? "" : old)) return fail("지금 쓰는 이메일과 같아요.");
        // 비밀번호가 있는 계정이면 확인한다 — 예전엔 소셜 연결(snsYn=Y)이면 비밀번호가 있어도 건너뛰었다
        boolean hasPassword = u.getPassword() != null && u.getPassword().startsWith("$2");
        boolean sns = "Y".equals(u.getSnsYn());
        if (hasPassword && !passwordEncoder.matches(password, u.getPassword())) return fail("비밀번호가 맞지 않아요.");
        if (!verification.isVerified(VerificationService.EMAIL, email, b.get("ticket") == null ? null : String.valueOf(b.get("ticket"))))
            return fail("새 이메일 인증을 먼저 마쳐 주세요.");
        if (users.findByEmail(email).filter(x -> x.getUserSn() != me).isPresent()
                || users.findByLoginId(email).filter(x -> x.getUserSn() != me).isPresent()) return fail("이미 다른 계정이 쓰는 이메일이에요.");

        boolean loginIdFollows = !sns && old != null && old.equalsIgnoreCase(u.getLoginId());
        u.setEmail(email);
        if (loginIdFollows) u.setLoginId(email);
        users.save(u);
        verification.consume(VerificationService.EMAIL, email);

        if (StringUtils.hasText(old) && mail.isEnabled()) {
            try {
                var st = stngs.findByUserSn(me);   // 받는 사람(본인)의 설정 언어로
                String lang = st != null && st.getLang() != null ? st.getLang() : com.knowra.cmm.i18n.Lang.current();
                mail.send(old, com.knowra.cmm.i18n.Msg.tl(lang, "[knowra] 계정 이메일이 바뀌었어요"),
                        com.knowra.cmm.i18n.Msg.tl(lang, "knowra 계정의 이메일이 {0} 로 바뀌었어요.\n본인이 바꾼 게 아니라면 바로 비밀번호를 재설정하고 문의해 주세요.", email));
            }
            catch (Exception ignore) { /* 안내 메일 실패가 변경을 되돌리지는 않는다 */ }
        }
        // loginId 가 토큰에 들어 있으니 새 액세스 토큰을 준다(세션은 그대로)
        ResultVO r = ok("email", email);
        r.putResult("loginIdChanged", loginIdFollows);
        r.putResult("accessToken", jwt.generateAccessToken(me, u.getLoginId(), currentSid(req)));
        return r;
    }

    private static ResultVO ok(String k, Object v) { ResultVO r = new ResultVO(); r.putResult(k, v); r.setResultCode(ResponseCode.SUCCESS.getCode()); return r; }
    private static ResultVO fail(String m) { ResultVO r = new ResultVO(); r.setResultCode(ResponseCode.SAVE_ERROR.getCode()); r.setResultMessage(m); return r; }
}
