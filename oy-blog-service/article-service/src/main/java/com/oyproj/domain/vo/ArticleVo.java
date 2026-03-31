package com.oyproj.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章VO
 */
@Data
public class ArticleVo {
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
}
