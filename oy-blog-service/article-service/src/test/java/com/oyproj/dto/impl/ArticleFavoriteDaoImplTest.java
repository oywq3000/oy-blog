package com.oyproj.dto.impl;

import com.oyproj.domain.entity.ArticleFavorite;
import com.oyproj.mapper.ArticleFavoriteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleFavoriteDaoImpl")
class ArticleFavoriteDaoImplTest {

    @Mock private ArticleFavoriteMapper baseMapper;
    private ArticleFavoriteDaoImpl dao;

    @BeforeEach
    void setUp() throws Exception {
        dao = new ArticleFavoriteDaoImpl();
        Field f = ArticleFavoriteDaoImpl.class.getSuperclass().getDeclaredField("baseMapper");
        f.setAccessible(true);
        f.set(dao, baseMapper);
    }

    @Test
    void listFavoritedArticleIds_returnsFromRows() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                ArticleFavorite.builder().articleId("a1").build(),
                ArticleFavorite.builder().articleId("a2").build()));
        assertEquals(List.of("a1", "a2"), dao.listFavoritedArticleIds("u1"));
        verify(baseMapper).selectList(any());
    }
}