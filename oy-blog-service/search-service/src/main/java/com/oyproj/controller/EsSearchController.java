package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
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
    public Result<PageVo<List<ArticleDocument>>> esSearch(SearchQueryDTO searchQueryDTO){

        //参数修正，前端传入的page参数从1开始
        if(searchQueryDTO.getPage()>=1){
            searchQueryDTO.setPage(searchQueryDTO.getPage()-1);
        }else{
            searchQueryDTO.setPage(0);
        }
        return searchBizService.searchArticles(searchQueryDTO);
    }
}
