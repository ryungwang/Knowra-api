package com.knowra.search.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * GET /api/search?q=…&type=all|posts|communities|users|tags&sort=relevance|latest|popular
     * &period=all|day|week|month|year&commSn=&page= — q 가 비면 인기 태그만. 비로그인도 쓸 수 있다.
     */
    @GetMapping("/api/search")
    public ResultVO search(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(required = false) String type,
                           @RequestParam(required = false) String sort,
                           @RequestParam(required = false) String period,
                           @RequestParam(required = false) Long commSn,
                           @RequestParam(required = false) Integer page,
                           @CurrentUser Long me) {
        return searchService.search(q, me, SearchService.Options.of(type, sort, period, commSn, page));
    }

    /** GET /api/search/suggest?q=… — 헤더 자동완성. */
    @GetMapping("/api/search/suggest")
    public ResultVO suggest(@RequestParam(value = "q", required = false) String q, @CurrentUser Long me) {
        return searchService.suggest(q, me);
    }
}
