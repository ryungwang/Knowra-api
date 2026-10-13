package com.knowra.badge;

import java.util.List;

/**
 * 뱃지 목록 — 단일 소스. 새 뱃지는 여기에 한 줄 추가하고 stat 이 BadgeService.stats 에 있는지만 본다.
 * stat: posts · comments · likesGot · followers · founded · joined · polls
 * 순서가 곧 화면 순서다(쉬운 것 → 어려운 것). key 는 저장되므로 <b>바꾸지 않는다</b>.
 */
public final class BadgeCatalog {

    private BadgeCatalog() {}

    public record Badge(String key, String name, String desc, String stat, int goal, String tone) {}

    public static final List<Badge> ALL = List.of(
            new Badge("FIRST_POST",     "첫 글",        "처음으로 글을 썼어요",              "posts",     1,   "ink"),
            new Badge("FIRST_COMMENT",  "첫 댓글",      "처음으로 댓글을 남겼어요",          "comments",  1,   "talk"),
            new Badge("FIRST_POLL",     "첫 투표",      "투표가 있는 글을 올렸어요",          "polls",     1,   "talk"),
            new Badge("JOINED_5",       "동네 마실",    "커뮤니티 5곳에 참여했어요",          "joined",    5,   "warm"),
            new Badge("FOUNDER",        "동네 만든 이", "커뮤니티를 직접 만들었어요",          "founded",   1,   "warm"),
            new Badge("POSTS_10",       "꾸준한 글쓴이", "글 10개를 썼어요",                 "posts",     10,  "ink"),
            new Badge("LIKED_10",       "작은 박수",    "글이 좋아요 10개를 받았어요",         "likesGot",  10,  "like"),
            new Badge("FOLLOWERS_10",   "이웃 열 명",   "팔로워가 10명이 됐어요",             "followers", 10,  "warm"),
            new Badge("COMMENTS_100",   "수다쟁이",     "댓글·답글 100개를 남겼어요",          "comments",  100, "talk"),
            new Badge("POSTS_50",       "동네 기록가",  "글 50개를 썼어요",                  "posts",     50,  "ink"),
            new Badge("LIKED_100",      "큰 박수",      "글이 좋아요 100개를 받았어요",        "likesGot",  100, "like")
    );

    public static Badge byKey(String key) {
        return ALL.stream().filter(b -> b.key().equals(key)).findFirst().orElse(null);
    }
}
