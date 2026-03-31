// G:\\JavaWorkSpace\\oy-blog\\oy-log-common\\src\\main\\java\\com\\oyproj\\common\\constant\\HeaderConstant.java

package com.oyproj.common.constant;

public enum HeaderConstant {
    // 用户相关
    USER_ID("X-User-Id"),
    USER_NAME("X-User-Name"),
    USER_ROLE("X-User-Role"),
    USER_DATA("X-User-Data"),
    
    // 认证相关
    AUTHORIZATION("Authorization"),
    BEARER_PREFIX("Bearer "),
    
    // 其他
    CONTENT_TYPE("Content-Type"),
    ACCEPT("Accept");
    
    private final String value;
    
    HeaderConstant(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
}