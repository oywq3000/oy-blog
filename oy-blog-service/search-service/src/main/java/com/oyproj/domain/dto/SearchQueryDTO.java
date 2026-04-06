package com.oyproj.domain.dto;

import lombok.Data;

@Data
public class SearchQueryDTO {
    private String p;
    private Integer page;
    private Integer size;
    private String keyword;
    private String author;
    private String tag;
    private String category;
    private String status;
}
