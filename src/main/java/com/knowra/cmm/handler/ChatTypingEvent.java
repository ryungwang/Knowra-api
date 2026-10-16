package com.knowra.cmm.handler;

/** 웹소켓 TYPING — 처리기가 ChatService 를 직접 부르면 서로를 물고 돈다(ChatService 가 처리기를 쓴다). 이벤트로 넘긴다 */
public record ChatTypingEvent(long userSn, long roomSn) { }
