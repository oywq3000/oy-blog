package com.oyproj.service.impl;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.entity.Article;
import com.oyproj.dto.ArticleDao;
import com.oyproj.dto.ArticleFavoriteDao;
import com.oyproj.dto.ArticleLikeDao;
import com.oyproj.dto.ArticleLogDao;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.service.ArticleEventPublisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("互动方法的行为事件发布位置")
class ArticleInteractionEventPublishTest {

    @Mock private ArticleLogDao viewDao;
    @Mock private ArticleStatsDao statsDao;
    @Mock private ArticleLikeDao likeDao;
    @Mock private ArticleFavoriteDao favoriteDao;
    @Mock private ArticleDao articleDao;
    @Mock private CommonCache<Object> commonCache;
    @Mock private UserClient userClient;
    @Mock private ArticleEventPublisher eventPublisher;

    private ArticleInteractionBizServiceImpl service;

    /** Result.ok() 依赖 I18nUtils 的静态 messageSource；纯 Mockito 环境必须反射注入 */
    @BeforeAll
    static void initMessageSource() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mock(MessageSource.class));
    }

    @BeforeEach
    void setUp() {
        // view() 内部通过 getClientIp()/getUserId() 读请求上下文，不设置会 NPE
        MockHttpServletRequest request = new MockHttpServletRequest();
        // getUserId() 读的是 X-User-Id 头；不注入则 userId 为 null，
        // 与本测试中各用例按 "U1" 写的桩值不匹配（strict stubbing 会直接报参数不匹配）
        request.addHeader("X-User-Id", "U1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        service = spy(new ArticleInteractionBizServiceImpl(
                viewDao, statsDao, likeDao, favoriteDao, articleDao, commonCache,
                userClient, eventPublisher));
    }

    @Test
    void like_whenStateChanged_publishesLikeEvent() {
        when(likeDao.hasLiked("A1", "U1")).thenReturn(false);

        service.like("A1");

        // 混用裸值与匹配器会被 Mockito 拒绝（InvalidUseOfMatchers），统一用 eq(...)
        verify(eventPublisher).publishLike(eq("A1"), any(), eq(false));
    }

    @Test
    void like_whenAlreadyLiked_doesNotPublish() {
        when(likeDao.hasLiked("A1", "U1")).thenReturn(true);

        service.like("A1");

        verify(eventPublisher, never()).publishLike(any(), any(), anyBoolean());
    }

    @Test
    void unlike_whenStateChanged_publishesCancelEvent() {
        when(likeDao.hasLiked("A1", "U1")).thenReturn(true);

        service.unlike("A1");

        verify(eventPublisher).publishLike(eq("A1"), any(), eq(true));
    }

    @Test
    void favorite_whenStateChanged_publishesFavoriteEvent() {
        when(favoriteDao.hasFavorited("A1", "U1")).thenReturn(false);

        service.favorite("A1");

        verify(eventPublisher).publishFavorite(eq("A1"), any(), eq(false));
    }

    @Test
    void unfavorite_whenStateChanged_publishesCancelEvent() {
        when(favoriteDao.hasFavorited("A1", "U1")).thenReturn(true);

        service.unfavorite("A1");

        verify(eventPublisher).publishFavorite(eq("A1"), any(), eq(true));
    }

    @Test
    void view_whenNotDeduplicated_publishesViewEvent() {
        when(articleDao.getById("A1")).thenReturn(Article.builder().id("A1").build());
        when(commonCache.hasKey(any())).thenReturn(false);

        service.view("A1");

        verify(eventPublisher).publishView(eq("A1"), any());
    }

    @Test
    void view_whenIpDeduplicated_doesNotPublish() {
        when(articleDao.getById("A1")).thenReturn(Article.builder().id("A1").build());
        when(commonCache.hasKey(any())).thenReturn(true);

        service.view("A1");

        verify(eventPublisher, never()).publishView(any(), any());
    }
}
