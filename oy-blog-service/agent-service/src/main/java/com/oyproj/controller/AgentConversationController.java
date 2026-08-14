package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.dto.RenameRequest;
import com.oyproj.domain.vo.ConversationVo;
import com.oyproj.domain.vo.MessageVo;
import com.oyproj.service.AgentChatService;
import com.oyproj.service.AgentConversationService;
import com.oyproj.utils.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 会话接口（网关 StripPrefix=1 后基路径为 /conversations）
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/conversations")
public class AgentConversationController {

    private final AgentConversationService conversationService;
    private final AgentChatService chatService;

    /**
     * 分页查询当前用户会话列表
     */
    @GetMapping
    public Result<PageVo<List<ConversationVo>>> list(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return Result.ok(conversationService.listConversations(CurrentUserUtil.getUserId(), page, size));
    }

    /**
     * 查询会话历史消息
     */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVo>> messages(@PathVariable String id) {
        return Result.ok(conversationService.listMessages(CurrentUserUtil.getUserId(), id));
    }

    /**
     * 删除会话（消息级联删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        chatService.stop(id);
        conversationService.delete(CurrentUserUtil.getUserId(), id);
        return Result.ok();
    }

    /**
     * 重命名会话
     */
    @PatchMapping("/{id}")
    public Result<Void> rename(@PathVariable String id, @RequestBody RenameRequest req) {
        conversationService.rename(CurrentUserUtil.getUserId(), id, req.getTitle());
        return Result.ok();
    }

    /**
     * 停止生成（前端点击停止时调用；前端 abort 本地方案时走 emitter 清理，双路径幂等）
     */
    @PostMapping("/{id}/stop")
    public Result<Void> stop(@PathVariable String id) {
        chatService.stop(id);
        return Result.ok();
    }
}
