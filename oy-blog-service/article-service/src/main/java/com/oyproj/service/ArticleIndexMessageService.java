package com.oyproj.service;

import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.entity.Article;

import java.util.List;

/**
 * 文章 ES 索引消息构建与发送（原 ArticleBizServiceImpl 私有方法原样迁出）。
 * 迁出原因：人工审核通过（发布旁路）也要发索引消息，两处共用。
 */
public interface ArticleIndexMessageService {

    /** 文章标签名列表（索引消息用，发布与审核通过共用） */
    List<String> loadTagNames(String articleId);

    /** 在事务提交后发送索引消息（afterCommit：消费方读到的数据已提交）；无活动事务时直接发送兜底（不丢 ES 消息） */
    void sendIndexAfterCommit(Article article, List<String> tags, MQOperation operation);

    /** 构建索引消息（逻辑与抽取前完全一致，tags 改为入参） */
    ArticleIndexMessage buildIndexMessage(Article article, List<String> tags, MQOperation operation);
}
