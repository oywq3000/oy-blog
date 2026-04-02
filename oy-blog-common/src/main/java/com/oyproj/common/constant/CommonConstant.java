package com.oyproj.common.constant;

public enum CommonConstant {

    //通用UserDTO键名
    USER_DATA("userDTO"),
    GUEST_ID("1000000000000000000");

    private final String value;

    CommonConstant(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
