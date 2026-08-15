package com.oyproj.common.constant;

public enum CachePrefix {


    USER_DTO("userDTO"),
    TOKEN("TOKEN"),
    REFRESH_TOKEN("REFRESH_TOKEN"),
    GUEST_ID("GUEST_ID"),
    USER_ID("USER_ID"),
    EMAIL_VERIFY_CODE("EMAIL_VERIFY_CODE"),
    EMAIL_VERIFY_TOKEN("EMAIL_VERIFY_TOKEN");

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
