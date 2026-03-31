package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.ArticleContent;
import com.oyproj.dto.ArticleContentDao;
import com.oyproj.mapper.ArticleContentMapper;
import org.springframework.stereotype.Repository;

/**
 * 文章内容数据访问实现
 */
@Repository
public class ArticleContentDaoImpl extends ServiceImpl<ArticleContentMapper, ArticleContent> implements ArticleContentDao {
}
