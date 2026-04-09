package com.knowra.post.event;

import java.time.LocalDateTime;

public record PostCreatedEvent(
        long postSn,
        String postKind,
        long userSn,
        Long commSn,
        LocalDateTime createdAt
) {}
