package com.oyproj.domain.dto;

import lombok.Data;

@Data
public class SearchQueryDTO {
    private String p;
    private int page;
    private int size;
}
