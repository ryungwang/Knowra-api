package com.knowra.user.event;

public record UserFollowChangedEvent(long myUserSn, long targetUserSn, boolean followed) {}
