package com.oyproj.domain.dto;

import com.oyproj.domain.common.SearchFitter;
import lombok.Data;

@Data
public class SearchQueryDTO {
    private String keyword;
    private Integer pageNum;
    private Integer pageSize;
    private String author;
    private SearchFitter filter;
    private String status;
    /** 排序字段: relevance | createdAt | likeCount | viewCount */
    private String sortBy;
    /** 排序方向: asc | desc（默认 desc） */
    private String sortOrder;
    /** 发布时间范围-起始 (yyyy-MM-dd 或 yyyy-MM-ddTHH:mm:ss) */
    private String dateFrom;
    /** 发布时间范围-结束 (含当日，自动补齐到 23:59:59.999) */
    private String dateTo;
}
