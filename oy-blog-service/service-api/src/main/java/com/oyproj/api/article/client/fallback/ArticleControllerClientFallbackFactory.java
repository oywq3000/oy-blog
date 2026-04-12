package com.oyproj.api.article.client.fallback;

import com.oyproj.api.article.client.ArticleControllerClient;
import com.oyproj.api.article.domain.UserArticleStatDto;
import com.oyproj.common.base.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ArticleControllerClientFallbackFactory implements FallbackFactory<ArticleControllerClient> {
    @Override
    public ArticleControllerClient create(Throwable cause) {
        return new ArticleControllerClient(){
            @Override
            public Result<UserArticleStatDto> getUserStats(String userId) {
                log.error("get userstats error");
                return null;
            }
        };
    }
}
