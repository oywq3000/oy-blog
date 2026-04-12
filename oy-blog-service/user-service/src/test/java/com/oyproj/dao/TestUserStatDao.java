package com.oyproj.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestUserStatDao {
    @Autowired
    private UserStatDao userStatDao;
    @Test
    void test(){
        com.oyproj.api.article.domain.UserArticleStatDto userStatById = userStatDao.getUserStatById("2038824018961887237");
        System.out.println(userStatById);

    }
}
