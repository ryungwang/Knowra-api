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
    private final com.knowra.chat.service.ChatGroupService groupService;

    /** 보낸 메시지 지우기 — body: msgSn */
    @PostMapping("/deleteMsg")
    public ResultVO deleteMsg(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return chatService.deleteMsg(b, me); }

    /** 그룹 만들기 — body: name, nickNames[] (초대받은 사람은 요청함으로) */
    @PostMapping("/createGroup")
    public ResultVO createGroup(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return wrap(() -> Map.of("roomSn", groupService.create(me, String.valueOf(b.get("name")), (java.util.Collection<?>) b.get("nickNames"))));
    }

    /** 초대 — body: roomSn, nickNames[] (방을 만든 사람만) */
    @PostMapping("/inviteGroup")
    public ResultVO inviteGroup(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return wrap(() -> Map.of("added", groupService.invite(me, Long.parseLong(String.valueOf(b.get("roomSn"))), (java.util.Collection<?>) b.get("nickNames"))));
    }

    /** 그룹 나가기 — body: roomSn */
    @PostMapping("/leaveRoom")
    public ResultVO leaveRoom(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return wrap(() -> { groupService.leave(me, Long.parseLong(String.valueOf(b.get("roomSn")))); return Map.of("left", true); });
    }

    /** 모임 대화방 열기 — body: eventSn (모임을 만든 사람·운영진) */
    @PostMapping("/eventRoom")
    public ResultVO eventRoom(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return wrap(() -> Map.of("roomSn", groupService.forEvent(me, Long.parseLong(String.valueOf(b.get("eventSn"))))));
    }

    private ResultVO wrap(java.util.function.Supplier<Map<String, ?>> body) {
        ResultVO r = new ResultVO();
        try {
            body.get().forEach(r::putResult);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            com.knowra.cmm.errlog.ErrorLog.report(e);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(com.knowra.cmm.model.ResponseCode.SAVE_ERROR.getMessage());
        }
        return r;
    }

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
