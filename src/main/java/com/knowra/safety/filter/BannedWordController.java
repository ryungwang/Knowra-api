package com.knowra.safety.filter;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 금칙어 관리 — /api/admin/** 라 관리자 전용(SecurityConfig). */
@RestController
@RequestMapping("/api/admin/console")
@RequiredArgsConstructor
public class BannedWordController {

    private final JdbcTemplate jdbc;
    private final ContentFilter filter;
    private final com.knowra.admin.service.AdminLogService adminLog;

    @PostMapping("/bannedWords")
    public ResultVO list() {
        ResultVO r = new ResultVO();
        // mode: PART(들어 있으면) | WORD(낱말로 있으면) — 낱말의 글자가 정한다(BannedWords)
        r.putResult("list", jdbc.queryForList("""
                SELECT WORD_SN AS "wordSn", WORD AS "word", FRST_CRT_DT AS "createdAt"
                FROM knowra_com.tbl_banned_word ORDER BY WORD_SN DESC""").stream()
                .map(m -> { var row = new java.util.LinkedHashMap<>(m); row.put("mode", BannedWords.modeOf(String.valueOf(m.get("word"))).name()); return row; })
                .toList());
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: word — 기호·공백은 비교 때 지우므로 두 글자 이상 남아야 한다. */
    @PostMapping("/bannedWordAdd")
    public ResultVO add(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        String word = String.valueOf(b.getOrDefault("word", "")).trim();
        if (ContentFilter.normalize(word).length() < 2 || word.length() > 50) {
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage("두 글자 이상(공백·기호 빼고), 50자 이하로 넣어 주세요. 한 글자는 엉뚱한 단어 속에서도 걸려요.");
            return r;
        }
        try {
            jdbc.update("INSERT INTO knowra_com.tbl_banned_word (WORD, CREATED_BY) VALUES (?, ?)", word, me);
        } catch (DuplicateKeyException e) {
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage("이미 있는 낱말이에요.");
            return r;
        }
        filter.invalidate();
        adminLog.record(me, "BANNED_ADD", null, null, word);
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: wordSn */
    @PostMapping("/bannedWordDelete")
    public ResultVO delete(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        long sn = Long.parseLong(String.valueOf(b.get("wordSn")));
        String word = jdbc.query("SELECT WORD FROM knowra_com.tbl_banned_word WHERE WORD_SN = ?", rs -> rs.next() ? rs.getString(1) : null, sn);
        jdbc.update("DELETE FROM knowra_com.tbl_banned_word WHERE WORD_SN = ?", sn);
        if (word != null) adminLog.record(me, "BANNED_DELETE", null, null, word);
        filter.invalidate();
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }
}
