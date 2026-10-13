package com.knowra.chat.controller;

import com.knowra.chat.service.ChatService;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 1:1 채팅. 전부 로그인 필수(SecurityConfig 기본). */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** body: roomSn, accept */
    @PostMapping("/respondRequest")
    public ResultVO respondRequest(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return chatService.respondRequest(params, me); }

    /** body(선택): box = main | request */
    @PostMapping("/getRoomList")
    public ResultVO getRoomList(@RequestBody(required = false) Map<String, Object> params, @CurrentUser Long me) {
        return chatService.getRoomList(me, params == null ? "main" : String.valueOf(params.getOrDefault("box", "main")));
    }

    /** params: nickName — 상대 닉네임. 방이 없으면 만든다. */
    @PostMapping("/openDm")
    public ResultVO openDm(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return chatService.openDm(params, me);
    }

    /** params: roomSn, [cursor], [size] */
    @PostMapping("/getMsgList")
    public ResultVO getMsgList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return chatService.getMsgList(params, me);
    }

    /** params: roomSn, msgCntnt */
    @PostMapping("/sendMsg")
    public ResultVO sendMsg(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return chatService.sendMsg(params, me);
    }

    /** params: roomSn */
    @PostMapping("/markRead")
    public ResultVO markRead(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return chatService.markRead(params, me);
    }
}
