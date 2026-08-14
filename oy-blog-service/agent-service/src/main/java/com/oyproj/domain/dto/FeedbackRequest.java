package com.oyproj.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息反馈请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    /**
     * 反馈类型：like / dislike
     */
    private String feedback;
}
