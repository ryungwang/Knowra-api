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

    /** GET /api/search?q=… — q 가 비면 인기 태그만. 비로그인도 쓸 수 있다. */
    @GetMapping("/api/search")
    public ResultVO search(@RequestParam(value = "q", required = false) String q, @CurrentUser Long me) {
        return searchService.search(q, me);
    }
}
