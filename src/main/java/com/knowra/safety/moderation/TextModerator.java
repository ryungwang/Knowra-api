package com.knowra.safety.moderation;

import java.util.List;

/**
 * 글 자동 검수 — 운영진이 모든 언어를 읽을 수 없어서, 해로운 글을 기계가 먼저 골라 신고함에 올린다.
 * 막지는 않는다(오탐이 나도 글이 사라지지 않게). 판단은 사람이 신고함에서 한다.
 *
 * <p>구현은 {@code knowra.moderation.provider} 로 고른다 — none(기본, 아무것도 보내지 않음) | openai.
 */
public interface TextModerator {

    /** 여러 글을 한 번에 — 결과는 같은 순서. 실패하면 예외(다음 차례에 다시 본다) */
    List<Verdict> check(List<String> texts);

    /** 걸린 분류와 점수(0~1). 비어 있으면 통과 */
    record Verdict(List<Flag> flags) {
        public boolean flagged() { return !flags.isEmpty(); }
    }

    record Flag(String category, double score) {}
}
