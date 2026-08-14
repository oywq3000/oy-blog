package com.oyproj.service;

import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.vo.ConversationVo;
import com.oyproj.domain.vo.MessageVo;

import java.util.List;

/**
 * AI 会话/消息业务接口
 */
public interface AgentConversationService {

    /**
     * 分页查询当前用户的会话列表
     */
    PageVo<List<ConversationVo>> listConversations(String userId, int page, int size);

    /**
     * 查询会话历史消息（owner 校验）
     */
    List<MessageVo> listMessages(String userId, String conversationId);

    /**
     * 删除会话（owner 校验，消息级联删除）
     */
    void delete(String userId, String conversationId);

    /**
     * 重命名会话（owner 校验）
     */
    void rename(String userId, String conversationId, String title);

    /**
     * 消息反馈（消息级 owner 校验）
     */
    void feedback(String userId, String messageId, String feedback);
}
