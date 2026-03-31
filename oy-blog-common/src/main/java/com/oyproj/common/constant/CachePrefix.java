package com.oyproj.common.constant;

public enum CachePrefix {


    USER_DTO("userDTO"),
    TOKEN("TOKEN"),
    REFRESH_TOKEN("REFRESH_TOKEN");

    private final String value;

    CachePrefix(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
    public String getPrefix(){
        return "{"+this.name()+"}_";
    }
}
