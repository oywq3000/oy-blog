package com.oyproj.domain.dto;

import lombok.Data;

@Data
public class SearchQueryDTO {
    private String keyword;
    private Integer page;
    private Integer size;
    private String author;
    private String tag;
    private String category;
    private String status;
}
