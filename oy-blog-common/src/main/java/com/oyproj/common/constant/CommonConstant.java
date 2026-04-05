package com.oyproj.common.constant;

public enum CommonConstant {

    //通用UserDTO键名
    USER_DATA("userDTO"),
    GUEST("GUEST"),
    USER("USER");

    private final String value;

    CommonConstant(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
