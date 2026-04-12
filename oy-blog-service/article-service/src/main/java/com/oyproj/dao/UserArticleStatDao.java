package com.oyproj.dao;
import com.oyproj.api.article.domain.UserArticleStatDto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserArticleStatDao {
    //根据id获取用户所发布的文章数量、like数等
    UserArticleStatDto getArticleStatById(String userId);
}
