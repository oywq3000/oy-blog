package com.oyproj.dao;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserStatDao {
    //根据id获取用户所发布的文章数量、like数等
    com.oyproj.api.article.domain.UserArticleStatDto getUserStatById(String userId);
}
