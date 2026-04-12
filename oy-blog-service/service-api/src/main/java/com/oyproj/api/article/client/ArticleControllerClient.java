package com.oyproj.api.article.client;

import com.oyproj.api.article.client.fallback.ArticleControllerClientFallbackFactory;
import com.oyproj.api.article.domain.UserArticleStatDto;
import com.oyproj.common.base.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "article-service",fallbackFactory = ArticleControllerClientFallbackFactory.class)
public interface ArticleControllerClient {
    @GetMapping("/article/stats/{userId}")
    Result<UserArticleStatDto> getUserStats(@PathVariable("userId") String userId);
}
