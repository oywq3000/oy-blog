package com.oyproj.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CommentWrapperVo {
    //总评论数（包含回复评论数量）
    private long totalCommentCount;
    //评论列表
    private List<CommentVo> items;
}
