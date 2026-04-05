package com.oyproj.utils;

import com.oyproj.common.constant.CachePrefix;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class GuestUtil {

    //解析Cookie得到
    public static String getGuestIdFromCookie(ServerHttpRequest request) {
        List<String> cookies = request.getHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.contains("GUEST_ID=")) {
                    // 解析Cookie值
                    String[] parts = cookie.split(";");
                    for (String part : parts) {
                        if (part.trim().startsWith("GUEST_ID=")) {
                            return part.trim().substring("GUEST_ID=".length());
                        }
                    }
                }
            }
        }
        return null;
    }

    //设置Cookie给返回结果
    public static void setGuestCookie(ServerHttpResponse response,String guestId){
        ResponseCookie cookie = ResponseCookie.from("GUEST_ID", guestId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Lax")
                .build();
        response.addCookie(cookie);
    }
    //生成GuestId
    public static String generateUniqueGuestId() {
        return CachePrefix.GUEST_ID.getPrefix() +
                UUID.randomUUID().toString().replace("-", "");
    }
}
