package com.oyproj.service.impl;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.common.util.MarkdownSanitizer;
import com.oyproj.domain.entity.Article;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.domain.entity.ArticleStats;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.dto.ArticleTagDao;
import com.oyproj.service.ArticleIndexMessageService;
import com.oyproj.service.ArticleMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;

/**
 * 文章 ES 索引消息构建与发送（原 ArticleBizServiceImpl 私有方法原样迁出）。
 * 迁出原因：人工审核通过（发布旁路）也要发索引消息，两处共用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleIndexMessageServiceImpl implements ArticleIndexMessageService {

    private final ArticleContentDao contentDao;
    private final ArticleStatsDao statsDao;
    private final ArticleMessageProducer articleMessageProducer;
    private final UserClient userClient;
    private final ArticleTagDao articleTagDao;

    /** 文章标签名列表（索引消息用，发布与审核通过共用） */
    @Override
    public List<String> loadTagNames(String articleId) {
        return articleTagDao.listTagNamesByArticleIds(Collections.singletonList(articleId))
                .getOrDefault(articleId, Collections.emptyList());
    }

    /** 在事务提交后发送索引消息（afterCommit：消费方读到的数据已提交）；无活动事务时直接发送兜底（不丢 ES 消息） */
    @Override
    public void sendIndexAfterCommit(Article article, List<String> tags, MQOperation operation) {
        ArticleIndexMessage message = buildIndexMessage(article, tags, operation);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            articleMessageProducer.sendArticleIndexMessage(message);
                        }
                    }
            );
        } else {
            // 防御：事务代理链未生效（如消费端代理异常时）直接发送，避免索引消息丢失
            articleMessageProducer.sendArticleIndexMessage(message);
            log.warn("无活动事务，直接发送索引消息, articleId: {}", article.getId());
        }
    }

    /** 构建索引消息（逻辑与抽取前完全一致，tags 改为入参） */
    @Override
    public ArticleIndexMessage buildIndexMessage(Article article, List<String> tags, MQOperation operation) {
        ArticleIndexMessage message = new ArticleIndexMessage();
        message.setOperation(operation);
        message.setArticleId(article.getId());
        message.setSlug(article.getSlug());
        message.setTitle(article.getTitle());
        message.setSummary(article.getSummary());
        message.setAuthorId(article.getAuthorId());
        try {
            Result<UserDTO> userDTO = userClient.getUserDTO(article.getAuthorId());
            if (userDTO != null && userDTO.getData() != null) {
                message.setAuthorName(userDTO.getData().getUsername());
                message.setAuthorAvatar(userDTO.getData().getAvatarUrl());
            }
        } catch (Exception e) {
            log.warn("获取作者信息失败, authorId: {}", article.getAuthorId(), e);
            message.setAuthorName(article.getAuthorId()); // 兜底：用 authorId
        }
        message.setCreatedAt(article.getCreatedAt());
        message.setPublishAt(article.getPublishAt());
        message.setUpdatedAt(article.getUpdatedAt());
        message.setStatus(article.getStatus());
        message.setTags(tags);

        // 加载文章内容（清洗 Markdown 为纯文本）
        try {
            ArticleContent content = contentDao.getById(article.getId());
            if (content != null) {
                message.setContentMd(MarkdownSanitizer.sanitize(content.getContentMd()));
            }
        } catch (Exception e) {
            log.warn("加载文章内容失败, articleId: {}", article.getId(), e);
        }

        // 加载统计数据
        try {
            ArticleStats stats = statsDao.getById(article.getId());
            if (stats != null) {
                message.setViewCount(stats.getViews());
                message.setLikeCount(stats.getLikes());
                message.setCommentCount(stats.getComments());
            }
        } catch (Exception e) {
            log.warn("加载文章统计失败, articleId: {}", article.getId(), e);
        }

        return message;
    }
}
