package com.knowra.event;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

/** 커뮤니티 모임. 목록·상세는 비로그인도(공개 커뮤니티), 나머지는 로그인. */
@RestController
@RequestMapping("/api/community/event")
@RequiredArgsConstructor
public class CommEventController {

    private final CommEventService svc;

    @GetMapping("/list")
    public ResultVO list(@RequestParam long commSn, @RequestParam(defaultValue = "upcoming") String scope, @CurrentUser Long me) {
        return run(() -> Map.of("list", svc.list(commSn, scope, me)));
    }

    @GetMapping("/detail")
    public ResultVO detail(@RequestParam long eventSn, @CurrentUser Long me) { return run(() -> Map.of("event", svc.detail(eventSn, me))); }

    @PostMapping("/save")
    public ResultVO save(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return run(() -> Map.of("event", svc.save(b, me))); }

    /** body: eventSn, going */
    @PostMapping("/rsvp")
    public ResultVO rsvp(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(() -> Map.of("event", svc.rsvp(Long.parseLong(String.valueOf(b.get("eventSn"))), Boolean.parseBoolean(String.valueOf(b.get("going"))), me)));
    }

    /** body: eventSn */
    @PostMapping("/cancel")
    public ResultVO cancel(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return run(() -> Map.of("event", svc.cancel(Long.parseLong(String.valueOf(b.get("eventSn"))), me)));
    }

    private ResultVO run(Supplier<Map<String, Object>> f) {
        ResultVO r = new ResultVO();
        try { f.get().forEach(r::putResult); r.setResultCode(ResponseCode.SUCCESS.getCode()); }
        catch (IllegalArgumentException e) { r.setResultCode(ResponseCode.SAVE_ERROR.getCode()); r.setResultMessage(e.getMessage()); }
        return r;
    }
}
