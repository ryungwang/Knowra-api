package com.knowra.post.event;

/**
 * 좋아요/댓글 발생 시 POPULAR 피드 점수 갱신용 이벤트.
 * scoreDelta: +1 추가, -1 취소
 */
public record PostReactedEvent(long postSn, String postKind, String actionType, int scoreDelta) {}
