package com.knowra.cmm.errlog;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.common.util.ComUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 화면 오류 접수(누구나 — 로그인 전 화면도 깨질 수 있다) + 관리자 조회.
 * 접수는 RateLimitInterceptor 가 사람·IP 당 분당 20회로 묶는다(오류 루프가 DB 를 채우지 않게).
 */
@RestController
@RequiredArgsConstructor
public class ErrorLogController {

    private static final int PAGE = 30;

    private final ErrorCollector collector;
    private final JdbcTemplate jdbc;

    /** body: message, stack, url, kind(error|rejection|render) */
    @PostMapping("/api/err/report")
    public ResultVO report(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        String message = ComUtil.getStrValue(b, "message");
        String stack = ComUtil.getStrValue(b, "stack");
        String kind = ComUtil.getStrValue(b, "kind");
        // 지문: 메시지 + 스택 첫 세 줄. 배포마다 바뀌는 번들 파일명의 해시와 줄:칸 번호는 지운다.
        String[] lines = stack.split("\n");
        StringBuilder fp = new StringBuilder(kind).append('|').append(message.replaceAll("\\d+", "#")).append('|');
        for (int i = 0; i < lines.length && i < 4; i++)
            // 스택 첫 줄엔 메시지가 그대로 들어 있어 숫자(id 등)도 지운다
            fp.append(lines[i].replaceAll("-[A-Za-z0-9_]{6,}\\.js", ".js").replaceAll("\\d+", "#")).append('|');
        String type = kind.isEmpty() ? "WebError" : "Web:" + kind;
        collector.record("WEB", type, message, stack, fp.toString(), ComUtil.getStrValue(b, "url"), me);
        ResultVO r = new ResultVO();
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: resolved(bool, 기본 false), page */
    @PostMapping("/api/admin/console/errorList")
    public ResultVO list(@RequestBody(required = false) Map<String, Object> b) {
        boolean resolved = b != null && Boolean.TRUE.equals(b.get("resolved"));
        int page = b == null || b.get("page") == null ? 0 : Math.max(0, Integer.parseInt(b.get("page").toString()));
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT ERR_SN AS "errSn", SOURCE AS "source", ERR_TYPE AS "errType", MESSAGE AS "message", STACK AS "stack",
                       LAST_PATH AS "lastPath", LAST_USER_SN AS "lastUserSn", CNT AS "cnt",
                       FIRST_SEEN AS "firstSeen", LAST_SEEN AS "lastSeen"
                FROM knowra_com.tbl_err_log WHERE RESOLVED = ? ORDER BY LAST_SEEN DESC LIMIT ? OFFSET ?
                """, resolved, PAGE, page * PAGE);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_com.tbl_err_log WHERE RESOLVED = ?", Integer.class, resolved);
        ResultVO r = new ResultVO();
        r.putResult("list", list);
        r.putResult("total", total);
        r.putResult("hasMore", (page + 1) * PAGE < (total == null ? 0 : total));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: errSn, resolved(bool) — 해결 표시해도 다시 나면 자동으로 열린다. */
    @PostMapping("/api/admin/console/errorResolve")
    public ResultVO resolve(@RequestBody Map<String, Object> b) {
        long errSn = Long.parseLong(String.valueOf(b.get("errSn")));
        boolean resolved = !Boolean.FALSE.equals(b.get("resolved"));
        int n = jdbc.update("UPDATE knowra_com.tbl_err_log SET RESOLVED = ? WHERE ERR_SN = ?", resolved, errSn);
        ResultVO r = new ResultVO();
        r.setResultCode(n == 1 ? ResponseCode.SUCCESS.getCode() : ResponseCode.SAVE_ERROR.getCode());
        if (n != 1) r.setResultMessage("없는 오류예요.");
        return r;
    }
}
