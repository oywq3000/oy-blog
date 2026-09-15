package com.oyproj.dto.impl;

import com.oyproj.domain.entity.ArticleTag;
import com.oyproj.mapper.ArticleTagMapper;
import com.oyproj.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArticleTagDaoImpl 标签查询")
class ArticleTagDaoImplTest {

    @Mock private ArticleTagMapper baseMapper;
    private ArticleTagDaoImpl dao;

    @BeforeEach
    void setUp() throws Exception {
        dao = new ArticleTagDaoImpl(mock(TagMapper.class));
        Field field = ArticleTagDaoImpl.class.getSuperclass().getDeclaredField("baseMapper");
        field.setAccessible(true);
        field.set(dao, baseMapper);
    }

    @Test
    void listTagIdsByArticleIds_distinct() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                ArticleTag.builder().articleId("a1").tagId("t1").build(),
                ArticleTag.builder().articleId("a2").tagId("t1").build(),
                ArticleTag.builder().articleId("a2").tagId("t2").build()));
        List<String> ids = dao.listTagIdsByArticleIds(List.of("a1", "a2"));
        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of("t1", "t2")));
        verify(baseMapper).selectList(any());
    }

    @Test
    void listTagIdsByArticleIds_empty_input_noMapperCall() {
        assertTrue(dao.listTagIdsByArticleIds(List.of()).isEmpty());
        verifyNoInteractions(baseMapper);
    }

    @Test
    void listByTagIds_returnsRelations() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                ArticleTag.builder().articleId("a1").tagId("t1").build()));
        List<ArticleTag> rows = dao.listByTagIds(List.of("t1"));
        assertEquals(1, rows.size());
        assertEquals("a1", rows.get(0).getArticleId());
    }

    @Test
    void listTagIdMapByArticleIds_groupsByArticle() {
        when(baseMapper.selectList(any())).thenReturn(List.of(
                ArticleTag.builder().articleId("a1").tagId("t1").build(),
                ArticleTag.builder().articleId("a1").tagId("t2").build(),
                ArticleTag.builder().articleId("a2").tagId("t1").build()));
        Map<String, List<String>> map = dao.listTagIdMapByArticleIds(List.of("a1", "a2"));
        assertEquals(List.of("t1", "t2"), map.get("a1"));
        assertEquals(List.of("t1"), map.get("a2"));
    }
}