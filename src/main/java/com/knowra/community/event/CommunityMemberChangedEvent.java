package com.knowra.community.event;

public record CommunityMemberChangedEvent(long userSn, long commSn, boolean joined) {}
