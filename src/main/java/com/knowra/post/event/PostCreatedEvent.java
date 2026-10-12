package com.knowra.post.event;

import java.time.LocalDateTime;

/** commSn 이 null 이면 전체 공개 글. */
public record PostCreatedEvent(
        long postSn,
        long userSn,
        Long commSn,
        LocalDateTime createdAt
) {}
