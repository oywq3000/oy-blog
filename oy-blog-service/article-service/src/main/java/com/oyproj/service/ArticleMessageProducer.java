package com.oyproj.service;

import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.common.mq.domain.ArticleIndexMessage;
import com.oyproj.domain.dto.ArticleSaveDto;
import com.oyproj.domain.entity.Article;

import java.util.List;

public interface ArticleMessageProducer {
    void sendArticleIndexMessage(ArticleIndexMessage message);
    void sendArticleDeleteMessage(String articleId);
    //void batchSendArticleIndexMessages(List<Article> articles, String operation);
}
