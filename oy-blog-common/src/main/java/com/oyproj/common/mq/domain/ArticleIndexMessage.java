package com.oyproj.common.mq.domain;

import com.oyproj.common.mq.constants.MQOperation;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章索引消息
 */
@Data
public class ArticleIndexMessage {
    
    /**
     * 操作类型：CREATE, UPDATE, DELETE
     */
    private MQOperation operation;
    
    /**
     * 文章ID
     */
    private String articleId;
    
    /**
     * 文章标题
     */
    private String title;

    /**
     * 文章摘要
     */
    private String summary;
    
    /**
     * 作者
     */
    private String author;
    
    /**
     * 作者ID
     */
    private String authorId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 标签列表
     */
    private List<String> tags;
    
    /**
     * 分类
     */
    private String category;
    
    /**
     * 操作时间
     */
    private LocalDateTime operationTime;
    
    public ArticleIndexMessage() {
        this.operationTime = LocalDateTime.now();
    }
}