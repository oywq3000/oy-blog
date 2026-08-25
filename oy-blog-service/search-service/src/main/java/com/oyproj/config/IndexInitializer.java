package com.oyproj.config;

import com.oyproj.domain.entity.ArticleDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 articles 索引存在并同步 mapping。
 * 使用 Spring Data API 读取 {@link ArticleDocument} 的 @Field 注解生成 mapping，
 * 比硬编码更灵活 — 实体类新增字段后，启动时通过 putMapping 增量同步进已有索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexInitializer implements ApplicationRunner {

    private final ElasticsearchOperations operations;

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOps = operations.indexOps(ArticleDocument.class);
            if (indexOps.exists()) {
                // ES 允许对已有索引追加新字段：putMapping 增量同步 @Field 声明的字段（如 publishAt）
                log.info("ES 索引 [articles] 已存在，同步 mapping...");
                indexOps.putMapping();
                return;
            }
            log.info("ES 索引 [articles] 不存在，根据 @Field 注解创建...");
            indexOps.createWithMapping();
            log.info("ES 索引 [articles] 创建成功");
        } catch (Exception e) {
            log.error("ES 索引 [articles] 初始化失败: {}", e.getMessage(), e);
        }
    }
}
