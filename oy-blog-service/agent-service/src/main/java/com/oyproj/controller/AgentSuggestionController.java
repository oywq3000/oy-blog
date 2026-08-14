package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.config.AgentProperties;
import com.oyproj.domain.vo.SuggestedQuestionVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 推荐问题接口（静态配置；后续可扩展为优先询问 Python，失败回落本配置）
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/suggestions")
public class AgentSuggestionController {

    private final AgentProperties agentProperties;

    @GetMapping
    public Result<List<SuggestedQuestionVo>> suggestions() {
        return Result.ok(agentProperties.getSuggestions());
    }
}
