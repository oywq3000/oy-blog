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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
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

    /**
     * 发布位置契约：落库 → 加统计 → 组装 VO → 最后才发事件。
     *
     * <p>本方法是增删改里唯一没有 {@code @Transactional} 的（落库不可回滚），而组装 VO
     * 是最后一个可能抛异常的点（它内部还要查 reaction/回复）。事件若发在组装之前，
     * 组装失败时调用方拿到 500、评论却已落库，用户重试就会重复落库，热榜也会重复计一次权重。
     * 断言必须带顺序：只要 publish 的位置回到组装之前，这里就会失败。</p>
     */
    @Test
    void addComment_publishesOnlyAfterVoAssembly() {
        CommentSaveDto dto = new CommentSaveDto();
        dto.setArticleId("A1");
        when(commentDao.getMaxFloor("A1")).thenReturn(0);

        service.addComment(dto);

        InOrder inOrder = inOrder(commentDao, statsDao, reactionDao, eventPublisher);
        inOrder.verify(commentDao).save(any());
        // incComments(String, long)：第二个实参必须是 eq(1L)，eq(1) 是 Integer，equals 匹配不上 Long
        inOrder.verify(statsDao).incComments(eq("A1"), eq(1L));
        inOrder.verify(reactionDao).getReactionCounts(anyList(), isNull());   // assembleCommentVos 内部
        inOrder.verify(eventPublisher).publish(eq("A1"), any(), eq(ArticleBehaviorType.COMMENT));
    }

    /**
     * 上一条契约的「反证」：组装 VO 抛异常时，事件绝不能已经发出去。
     * 这是发布位置提前时最早会红的那条断言。
     */
    @Test
    void addComment_whenVoAssemblyFails_doesNotPublishEvent() {
        CommentSaveDto dto = new CommentSaveDto();
        dto.setArticleId("A1");
        when(commentDao.getMaxFloor("A1")).thenReturn(0);
        when(reactionDao.getReactionCounts(anyList(), isNull()))
                .thenThrow(new RuntimeException("reaction 查询失败"));

        assertThrows(RuntimeException.class, () -> service.addComment(dto));

        verify(eventPublisher, never()).publish(any(), any(), any());
    }
}
