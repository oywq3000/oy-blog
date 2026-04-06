package com.oyproj;


import com.oyproj.common.mq.constants.MQOperation;
import com.oyproj.domain.dto.ArticleSaveDto;
import com.oyproj.domain.entity.Article;
import com.oyproj.service.ArticleMessageProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class testMQ {

    @Autowired
    private ArticleMessageProducer articleMessageProducer;

    @Test
    public void testMQ(){
        Article article = new Article();
        article.setId("12321312");
        ArticleSaveDto dto = new ArticleSaveDto();
        //articleMessageProducer.sendArticleIndexMessage(article,dto, MQOperation.CREATE);
    }
}
