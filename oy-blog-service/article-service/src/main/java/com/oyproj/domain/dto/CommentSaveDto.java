package com.oyproj.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 *  发表评论DTO
 */
@Data
public class CommentSaveDto {
    @NotBlank(message = "文章ID不能为空")
    private String articleId;
    
    @NotBlank(message = "评论内容不能为空")
    private String content;
}
