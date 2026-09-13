package com.oyproj.service.impl;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.service.ArticleIndexControllerProvider;
import com.oyproj.service.ArticleIndexSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 播种实现：分页取快照 → 逐条同步发送并确认 → 如实报告成功/失败。
 *
 * <p><b>为什么逐条确认而不是一把梭</b>：{@code max.block.ms: 100} 是方案 A 为避免阻塞
 * 用户请求而设的<b>全局生产者配置</b>。播种时首次发送可能要等 metadata（&gt;100ms）会抛超时。
 * 逐条确认才能<b>如实报告哪些失败了</b>，而不是假装成功——播种没成功就切换，
 * 重放能力从一开始就是残缺的，而这一点不会有人立刻发现。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleIndexSeedServiceImpl implements ArticleIndexSeedService {

    private static final int PAGE_SIZE = 100;
    /** 单条等待确认的上限；只用于统计，不影响生产者自身的重试 */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final ArticleIndexControllerProvider snapshotProvider;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public SeedResult seedIndexTopic() {
        int total = 0;
        int succeeded = 0;
        List<String> failed = new ArrayList<>();

        int pageNum = 0;
        while (true) {
            Result<PageVo<List<ArticleIndexMessage>>> result = snapshotProvider.snapshot(pageNum, PAGE_SIZE);
            if (result == null || result.getData() == null) {
                log.warn("播种：第 {} 页快照为空，提前结束", pageNum);
                break;
            }
            List<ArticleIndexMessage> batch = result.getData().getData();
            if (batch == null || batch.isEmpty()) {
                break;
            }
            total += batch.size();

            for (ArticleIndexMessage msg : batch) {
                String articleId = msg.getArticleId();
                try {
                    kafkaTemplate.send(KafkaTopicConfig.TOPIC_ARTICLE_INDEX, articleId, msg)
                            .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    succeeded++;
                } catch (Exception e) {
                    log.warn("播种：文章发送失败, articleId: {}", articleId, e);
                    failed.add(articleId);
                }
            }

            pageNum++;
        }

        log.info("播种完成：总数 {}, 成功 {}, 失败 {}", total, succeeded, failed.size());
        return new SeedResult(total, succeeded, failed);
    }
}
