package com.knowra.chat.service;

import java.util.Map;

/**
 * 메시지 하나가 저장됐다 — 커밋 뒤 {@link ChatPushListener} 가 접속하지 않은 받는 사람에게 웹 푸시를 보낸다.
 *
 * @param recipients 받는 사람 → 메시지 요청함 쪽인지(아직 수락 전). 거절·나간 사람과 차단 관계는 이미 뺐다.
 */
public record ChatMsgSentEvent(long roomSn, long senderSn, String text, boolean hasImage,
                               boolean group, String roomName, Map<Long, Boolean> recipients) {}
