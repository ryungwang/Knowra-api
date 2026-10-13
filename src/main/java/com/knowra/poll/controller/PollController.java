package com.knowra.poll.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.poll.service.PollService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/poll")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    /** body: pollSn, optionSns[] — 비우면 내 표 취소. 로그인 필수. */
    @PostMapping("/vote")
    public ResultVO vote(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return pollService.vote(params, me); }
}
