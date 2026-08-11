package com.oyproj.domain.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum SearchFitter {
    ALL("all"),
    ARTICLE("article"),
    TAG("tag"),
    AUTHOR("author");

    private final String filter;

    SearchFitter(String filter) {
        this.filter = filter;
    }

    @JsonValue
    public String getValue() {
        return filter;
    }

    @JsonCreator
    public static SearchFitter fromValue(String value) {
        if (value == null) return ALL;
        return Arrays.stream(values())
                .filter(f -> f.filter.equalsIgnoreCase(value))
                .findFirst()
                .orElse(ALL);
    }
}
