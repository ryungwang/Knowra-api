package com.knowra.search.service;

import com.knowra.post.entity.PostDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 검색 낱말 나누기·관련도 — DB 없이 도는 순수 계산만. */
class SearchRankingTest {

    private static PostDTO post(String title, String body, List<String> tags, int daysAgo) {
        PostDTO d = new PostDTO("NORMAL", 1L, null, null, null, 1L, "n", "n",
                title, body, LocalDateTime.now().minusDays(daysAgo), 0, 0, 0, null, false);
        d.setTagNms(tags);
        return d;
    }

    @Test
    void 낱말은_공백으로_나누고_샵과_중복을_뺀다() {
        assertThat(SearchService.tokens("  #코인   투자 코인 ")).containsExactly("코인", "투자");
        assertThat(SearchService.tokens("a b c d e f g")).hasSize(5);
    }

    @Test
    void 제목에_있으면_본문에만_있는_것보다_앞선다() {
        List<String> tk = List.of("코인");
        double inTitle = SearchService.relevance(post("코인 이야기", "", List.of(), 100), "코인", tk);
        double inBody  = SearchService.relevance(post("투자 이야기", "코인 얘기", List.of(), 100), "코인", tk);
        assertThat(inTitle).isGreaterThan(inBody);
    }

    @Test
    void 검색어_전체가_제목에_그대로_있으면_가산된다() {
        List<String> tk = List.of("코인", "투자");
        double phrase  = SearchService.relevance(post("월급쟁이 코인 투자 방법", "", List.of(), 100), "코인 투자", tk);
        double split   = SearchService.relevance(post("투자 말고 코인", "", List.of(), 100), "코인 투자", tk);
        assertThat(phrase).isGreaterThan(split);
    }

    @Test
    void 태그가_정확히_맞으면_부분일치보다_높다() {
        List<String> tk = List.of("코인");
        double exact   = SearchService.relevance(post("글", "", List.of("코인"), 100), "코인", tk);
        double partial = SearchService.relevance(post("글", "", List.of("코인시장"), 100), "코인", tk);
        assertThat(exact).isGreaterThan(partial);
    }

    @Test
    void 다른_언어로_옮긴_검색어로_맞은_글은_원문으로_맞은_글보다_조금_뒤() {
        List<SearchService.Query> qs = List.of(
                new SearchService.Query("등산", List.of("등산"), 1.0),
                new SearchService.Query("hiking", List.of("hiking"), SearchService.ALT_WEIGHT));
        double native_ = SearchService.relevance(post("주말 등산 코스", "", List.of(), 100), qs);
        double alt     = SearchService.relevance(post("Weekend hiking course", "", List.of(), 100), qs);
        assertThat(alt).isGreaterThan(0).isLessThan(native_);
    }
}
