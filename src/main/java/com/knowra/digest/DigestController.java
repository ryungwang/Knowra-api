package com.knowra.digest;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/** 주간 소식 메일. 미리보기(로그인), 한 번에 끄기(서명 링크 — 로그인 없이). 즉시 발송은 관리자 콘솔. */
@RestController
@RequiredArgsConstructor
public class DigestController {

    private final DigestService svc;

    @GetMapping("/api/digest/preview")
    public ResultVO preview(@CurrentUser Long me) {
        ResultVO r = new ResultVO();
        DigestService.Digest d = svc.build(me, LocalDateTime.now());
        r.putResult("empty", d.isEmpty());
        var p = svc.prefs(me);   // 미리보기도 실제로 받을 언어로
        r.putResult("subject", d.isEmpty() ? "" : svc.subject(d, p.lang()));
        r.putResult("text", d.isEmpty() ? "" : svc.render(d, me, p));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: u, t */
    @PostMapping("/api/digest/unsubscribe")
    public ResultVO unsubscribe(@RequestBody Map<String, Object> b) {
        ResultVO r = new ResultVO();
        try {
            svc.unsubscribe(Long.parseLong(String.valueOf(b.get("u"))), b.get("t") == null ? null : String.valueOf(b.get("t")));
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {   // NumberFormatException 포함
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(e instanceof NumberFormatException ? "링크가 올바르지 않아요. 설정 화면에서 꺼 주세요." : e.getMessage());
        }
        return r;
    }

    /**
     * 메일 프로그램의 「구독 해지」 한 번 누르기(RFC 8058) — 본문이 form 「List-Unsubscribe=One-Click」으로 온다.
     * 로그인 없이, 서명(t)으로만 확인한다. 응답 본문은 메일 프로그램이 보지 않으니 상태 코드만 맞춘다.
     */
    @PostMapping("/api/digest/oneclick")
    public org.springframework.http.ResponseEntity<Void> oneClick(@RequestParam("u") long u, @RequestParam("t") String t) {
        try { svc.unsubscribe(u, t); return org.springframework.http.ResponseEntity.ok().build(); }
        catch (IllegalArgumentException e) { return org.springframework.http.ResponseEntity.badRequest().build(); }
    }

    /** 관리자: 지금 바로 보내기(이번 주에 이미 받은 사람은 건너뛴다). {@code /api/admin/**} 는 ROLE_ADMIN 전용. */
    @PostMapping("/api/admin/console/digestRun")
    public ResultVO run() {
        ResultVO r = new ResultVO();
        svc.sendAll(LocalDateTime.now()).forEach(r::putResult);
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }
}
