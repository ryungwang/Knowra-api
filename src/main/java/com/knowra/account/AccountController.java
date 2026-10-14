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
    private final DataExportService dataExport;
    private final tools.jackson.databind.ObjectMapper objectMapper;

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
            try { mail.send(old, "[knowra] 계정 이메일이 바뀌었어요", "knowra 계정의 이메일이 " + email + " 로 바뀌었어요.\n본인이 바꾼 게 아니라면 바로 비밀번호를 재설정하고 문의해 주세요."); }
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
