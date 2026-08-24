package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.common.exception.ValidationException;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.domain.dto.FeedbackRequest;
import com.oyproj.service.AgentConversationService;
import com.oyproj.utils.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI 消息反馈接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/messages")
public class AgentMessageController {

    private final AgentConversationService conversationService;

    /**
     * 消息点赞/点踩
     */
    @PostMapping("/{messageId}/feedback")
    public Result<Void> feedback(@PathVariable String messageId, @RequestBody FeedbackRequest req) {
        if (!"like".equals(req.getFeedback()) && !"dislike".equals(req.getFeedback())) {
            throw new ValidationException(I18nUtils.t("message.feedback_type.invalid"));
        }
        conversationService.feedback(CurrentUserUtil.getUserId(), messageId, req.getFeedback());
        return Result.ok();
    }
}
