package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.domain.dto.SearchQueryDTO;
import com.oyproj.domain.entity.ArticleDocument;
import com.oyproj.service.SearchBizService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/essearch")
@RequiredArgsConstructor
public class EsSearchController {
    private final SearchBizService searchBizService;
    @GetMapping("/search")
    @Operation(summary = "EsSearchController",description = "")
    public Result<List<ArticleDocument>> esSearch(@RequestParam SearchQueryDTO searchQueryDTO){
        return searchBizService.searchArticles(searchQueryDTO);
    }
}
