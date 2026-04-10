package com.oyproj.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageVo<T> {
    private Integer currentPage;
    private Integer pageSize;
    private Long total;
    private Integer totalPages;
    private T data;
}
