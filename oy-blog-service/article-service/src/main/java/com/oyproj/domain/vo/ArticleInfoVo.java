package com.oyproj.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章VO
 */
@Data
public class ArticleInfoVo {
    private String id;
    private String title;
    private String authorId;
    private String status;
    private String summary;
    private String visibility;
    private Integer isTop;
    private String slug;
    private String coverUrl;
    private String language;
    private Integer allowComment;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateAt;

    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private Long favorites;

    private String authorName;
    private String authorAvatar;

    /**
     * 最近浏览时间（仅浏览历史接口返回）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime viewedAt;

    /**
     * 收藏时间（仅我的收藏接口返回）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime favoritedAt;
}
