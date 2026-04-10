package com.oyproj;

import com.oyproj.domain.entity.Article;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

public class testForCreateArticle {

    @Test
    public void testForArticle(){
        Article article = new Article();
        article.setCreatedAt(LocalDateTime.now());
        System.out.println(article);
    }

}
