package com.oyproj.service.impl;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.mq.constants.ArticleBehaviorType;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.dto.ArticleStatsDao;
import com.oyproj.dto.CommentDao;
import com.oyproj.dto.CommentReactionDao;
import com.oyproj.dto.CommentReplyDao;
import com.oyproj.domain.dto.CommentSaveDto;
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
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("发表评论的行为事件发布")
class ArticleCommentEventPublishTest {

    @Mock private CommentDao commentDao;
    @Mock private CommentReplyDao replyDao;
    @Mock private CommentReactionDao reactionDao;
    @Mock private UserClient userClient;
    @Mock private ArticleStatsDao statsDao;
    @Mock private ArticleEventPublisher eventPublisher;

    private ArticleCommentBizServiceImpl service;

    /** Result.ok() 依赖 I18nUtils 的静态 messageSource；纯 Mockito 环境必须反射注入 */
    @BeforeAll
    static void initMessageSource() throws Exception {
        Field field = I18nUtils.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, mock(MessageSource.class));
    }

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        // 评论落库后会组装展示 VO，其依赖（reaction/回复聚合）与"事件是否发出"无关，补齐以免 NPE
        lenient().when(reactionDao.getReactionCounts(anyList(), isNull())).thenReturn(Collections.emptyMap());
        lenient().when(reactionDao.getUserReactions(anyList(), isNull(), isNull())).thenReturn(Collections.emptyMap());
        lenient().when(replyDao.countByCommentIds(anyList())).thenReturn(Collections.emptyMap());
        service = spy(new ArticleCommentBizServiceImpl(
                commentDao, replyDao, reactionDao, userClient, statsDao, eventPublisher));
    }

    @Test
    void addComment_publishesCommentEvent() {
        CommentSaveDto dto = new CommentSaveDto();
        dto.setArticleId("A1");
        when(commentDao.getMaxFloor("A1")).thenReturn(0);

        service.addComment(dto);

        verify(eventPublisher).publish(eq("A1"), any(), eq(ArticleBehaviorType.COMMENT));
    }
}
