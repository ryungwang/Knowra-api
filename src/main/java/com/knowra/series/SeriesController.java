package com.knowra.series;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Consumer;

/** 연재 — 읽기(get)는 비로그인도, 나머지는 로그인 필수 */
@RestController
@RequestMapping("/api/series")
@RequiredArgsConstructor
public class SeriesController {

    private final SeriesService series;

    @GetMapping("/get")
    public ResultVO get(@RequestParam long seriesSn, @CurrentUser Long me) {
        return run(r -> r.putResult("series", series.get(seriesSn, me)));
    }

    @GetMapping("/byUser")
    public ResultVO byUser(@RequestParam String nickName, @CurrentUser Long me) {
        return run(r -> r.putResult("list", series.byUser(nickName, me)));
    }

    @PostMapping("/mine")
    public ResultVO mine(@CurrentUser Long me) {
        return run(r -> r.putResult("list", series.mine(me)));
    }

    /** body: title, desc */
    @PostMapping("/create")
    public ResultVO create(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(r -> r.putResult("seriesSn", series.create(me, str(b.get("title")), str(b.get("desc")))));
    }

    /** body: seriesSn, title, desc */
    @PostMapping("/update")
    public ResultVO update(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(r -> series.update(me, Long.parseLong(String.valueOf(b.get("seriesSn"))), str(b.get("title")), str(b.get("desc"))));
    }

    /** body: seriesSn */
    @PostMapping("/remove")
    public ResultVO remove(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(r -> series.remove(me, Long.parseLong(String.valueOf(b.get("seriesSn")))));
    }

    /** body: seriesSn, on(true=구독) */
    @PostMapping("/subscribe")
    public ResultVO subscribe(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(r -> series.subscribe(me, Long.parseLong(String.valueOf(b.get("seriesSn"))), Boolean.parseBoolean(String.valueOf(b.get("on")))));
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static ResultVO run(Consumer<ResultVO> body) {
        ResultVO r = new ResultVO();
        try {
            body.accept(r);
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        }
        return r;
    }
}
