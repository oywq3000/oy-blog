package com.oyproj.service.impl;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.mq.config.KafkaTopicConfig;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.service.ArticleIndexControllerProvider;
import com.oyproj.service.ArticleIndexSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * 用 {@link ObjectProvider} 而不是直接注入 {@code ArticleIndexControllerProvider}：
     * 该接口唯一的实现者就是 {@code ArticleIndexController}，而 controller 又要注入本 bean
     * ——构造期互相等待，Spring Boot 3 默认禁止循环引用 → <b>服务起不来</b>
     * （{@code BeanCurrentlyInCreationException}）。ObjectProvider 把解析推迟到调用时刻，
     * 构造期不再需要 controller，环就断了。
     *
     * <p>注意不要改用字段上的 {@code @Lazy}：Lombok {@code @RequiredArgsConstructor} 只搬它
     * 内置注解集，字段级 {@code @Lazy} 不保证能到构造器参数上。</p>
     */
    private final ObjectProvider<ArticleIndexControllerProvider> snapshotProvider;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public SeedResult seedIndexTopic() {
        int total = 0;
        int succeeded = 0;
        List<String> failed = new ArrayList<>();

        Long firstPageTotal = null;
        int pageNum = 0;
        while (true) {
            Result<PageVo<List<ArticleIndexMessage>>> result =
                    snapshotProvider.getObject().snapshot(pageNum, PAGE_SIZE);
            if (result == null || result.getData() == null) {
                log.warn("播种：第 {} 页快照为空，提前结束", pageNum);
                break;
            }
            PageVo<List<ArticleIndexMessage>> page = result.getData();

            // 每页都会重新 count 一次（ArticleIndexController.getIndexSnapshot 每次都取 countPublished）。
            // 若播种期间有人发布/删除文章，offset 分页就会漂移：整篇可能被跳过——它既不在
            // succeeded 里，也不在 failedArticleIds 里，只看失败清单是发现不了的。这里只报不拦
            // （诊断，不改控制流）；判据是每页的 PageVo.total 必须恒定。
            Long pageTotal = page.getTotal();
            if (pageTotal != null) {
                if (firstPageTotal == null) {
                    firstPageTotal = pageTotal;
                } else if (!firstPageTotal.equals(pageTotal)) {
                    log.warn("播种：分页期间文章总数发生变化（首页 {} → 第 {} 页 {}），"
                                    + "offset 分页可能已漂移、有文章被跳过，建议重跑并按 id 集合对账",
                            firstPageTotal, pageNum, pageTotal);
                }
            }

            List<ArticleIndexMessage> batch = page.getData();
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
