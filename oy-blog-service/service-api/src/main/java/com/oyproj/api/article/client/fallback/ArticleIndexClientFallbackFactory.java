package com.oyproj.api.article.client.fallback;

import com.oyproj.api.article.client.ArticleIndexClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ArticleIndexClientFallbackFactory implements FallbackFactory<ArticleIndexClient> {
    @Override
    public ArticleIndexClient create(Throwable cause) {
        return new ArticleIndexClient() {
            @Override
            public Result<PageVo<List<ArticleIndexMessage>>> getIndexSnapshot(int page, int size) {
                log.error("获取文章索引快照失败, page: {}, size: {}", page, size, cause);
                return null;
            }
        };
    }
}
