package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.domain.entity.AgentConversation;
import com.oyproj.domain.entity.AgentMessage;
import com.oyproj.domain.vo.ConversationVo;
import com.oyproj.domain.vo.MessageVo;
import com.oyproj.mapper.AgentConversationMapper;
import com.oyproj.mapper.AgentMessageMapper;
import com.oyproj.service.AgentConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentConversationServiceImpl implements AgentConversationService {

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;

    @Override
    public PageVo<List<ConversationVo>> listConversations(String userId, int page, int size) {
        // PageHelper ThreadLocal 分页（与 article-service 一致，项目全局排除了 jsqlparser，MP 分页插件不可用）
        PageHelper.startPage(page, size);
        List<AgentConversation> records = conversationMapper.selectList(
                new LambdaQueryWrapper<AgentConversation>()
                        .eq(AgentConversation::getUserId, userId)
                        .orderByDesc(AgentConversation::getUpdatedAt));
        PageInfo<AgentConversation> pageInfo = new PageInfo<>(records);
        PageHelper.clearPage();

        List<ConversationVo> vos = records.stream()
                .map(c -> ConversationVo.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .createdAt(c.getCreatedAt())
                        .updatedAt(c.getUpdatedAt())
                        .messageCount(0L)
                        .build())
                .collect(Collectors.toList());

        // 批量统计消息数，避免 N+1
        if (!vos.isEmpty()) {
            List<String> ids = vos.stream().map(ConversationVo::getId).collect(Collectors.toList());
            Map<String, Long> counts = new HashMap<>();
            for (Map<String, Object> row : messageMapper.countByConversationIds(ids)) {
                String convId = String.valueOf(row.get("conversation_id"));
                Object cnt = row.get("cnt");
                counts.put(convId, cnt == null ? 0L : Long.parseLong(String.valueOf(cnt)));
            }
            vos.forEach(vo -> vo.setMessageCount(counts.getOrDefault(vo.getId(), 0L)));
        }

        return new PageVo<>(pageInfo.getPageNum(), pageInfo.getPageSize(),
                pageInfo.getTotal(), pageInfo.getPages(), vos);
    }

    @Override
    public List<MessageVo> listMessages(String userId, String conversationId) {
        requireOwned(userId, conversationId);
        List<AgentMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AgentMessage>()
                        .eq(AgentMessage::getConversationId, conversationId)
                        .orderByAsc(AgentMessage::getCreatedAt));
        return messages.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public void delete(String userId, String conversationId) {
        requireOwned(userId, conversationId);
        // 物理删除，消息靠外键 ON DELETE CASCADE 级联删除
        conversationMapper.deleteById(conversationId);
    }

    @Override
    public void rename(String userId, String conversationId, String title) {
        AgentConversation conv = requireOwned(userId, conversationId);
        conv.setTitle(title);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conv);
    }

    @Override
    public void feedback(String userId, String messageId, String feedback) {
        int rows = messageMapper.update(null, new LambdaUpdateWrapper<AgentMessage>()
                .eq(AgentMessage::getId, messageId)
                .eq(AgentMessage::getUserId, userId)
                .set(AgentMessage::getFeedback, feedback));
        if (rows == 0) {
            throw new NotFoundException("消息不存在");
        }
    }

    /**
     * 会话 owner 校验：不存在或不属于当前用户一律按"不存在"处理（防枚举）
     */
    private AgentConversation requireOwned(String userId, String conversationId) {
        AgentConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new NotFoundException("会话不存在");
        }
        return conv;
    }

    private MessageVo toVo(AgentMessage m) {
        return MessageVo.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .thinking(m.getThinking())
                .thinkingTime(m.getThinkingTime())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
