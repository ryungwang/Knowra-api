package com.knowra.translate;

import com.knowra.cmm.security.CurrentUser;
import com.knowra.cmm.i18n.Lang;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 글·목록 제목·댓글·대화 메시지 번역. 언어는 to 가 없으면 지금 화면 언어.
 * 글·제목·댓글·커뮤니티는 로그인하지 않아도 묻는다 — 단 이미 해 둔 번역만 돌려준다(번역 한도를 아무나 쓰지 못하게, TranslationService.call).
 * 대화는 로그인한 멤버만.
 */
@RestController
@RequiredArgsConstructor
public class TranslateController {

    private final TranslationService svc;

    /** body: postSn, to? */
    @PostMapping("/api/translate/post")
    public ResultVO post(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(() -> svc.post(Long.parseLong(String.valueOf(b.get("postSn"))), me, to(b)));
    }

    /** body: postSn, cmtSns[], to? — 결과 result.list = {댓글번호: {status, body, from}} */
    @PostMapping("/api/translate/comments")
    public ResultVO comments(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        List<Long> sns = b.get("cmtSns") instanceof List<?> l ? l.stream().map(x -> Long.parseLong(String.valueOf(x))).toList() : List.of();
        return run(() -> Map.of("list", svc.comments(Long.parseLong(String.valueOf(b.get("postSn"))), sns, me, to(b))));
    }

    /** body: roomSn, msgSns[], to? — 결과 result.list = {메시지번호: {status, body, from}} */
    @PostMapping("/api/translate/messages")
    public ResultVO messages(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        List<Long> sns = b.get("msgSns") instanceof List<?> l ? l.stream().map(x -> Long.parseLong(String.valueOf(x))).toList() : List.of();
        return run(() -> Map.of("list", svc.messages(Long.parseLong(String.valueOf(b.get("roomSn"))), sns, me, to(b))));
    }

    /** body: postSns[], to? — 목록 카드 제목. 결과 result.list = {글번호: {status, title, from}} */
    @PostMapping("/api/translate/titles")
    public ResultVO titles(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        List<Long> sns = b.get("postSns") instanceof List<?> l ? l.stream().map(x -> Long.parseLong(String.valueOf(x))).toList() : List.of();
        return run(() -> Map.of("list", svc.titles(sns, me, to(b))));
    }

    /** body: commSn, to? — 커뮤니티 이름·소개·규칙. 결과 {status, from, name, desc, rules[]} */
    @PostMapping("/api/translate/community")
    public ResultVO community(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(() -> svc.community(Long.parseLong(String.valueOf(b.get("commSn"))), me, to(b)));
    }

    /** body: eventSns[], to? — 모임 제목·설명. 결과 result.list = {모임번호: {status, title, body, from}} */
    @PostMapping("/api/translate/events")
    public ResultVO events(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        List<Long> sns = b.get("eventSns") instanceof List<?> l ? l.stream().map(x -> Long.parseLong(String.valueOf(x))).toList() : List.of();
        return run(() -> Map.of("list", svc.events(sns, me, to(b))));
    }

    private static String to(Map<String, Object> b) {
        String t = b.get("to") == null ? null : Lang.normalize(String.valueOf(b.get("to")));
        return t != null && Lang.ALL.contains(t) ? t : Lang.current();
    }

    private static ResultVO run(Supplier<Map<String, ?>> f) {
        ResultVO r = new ResultVO();
        try {
            f.get().forEach(r::putResult);
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        }
        return r;
    }
}
