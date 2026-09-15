package com.oyproj.common;

/** 推荐功能 Redis 键约定：前缀 rec: 与现有 CachePrefix 区分，集中一处防拼写分散 */
public final class RecommendKeys {
    private RecommendKeys() {}
    public static String profileUser(String userId)    { return "rec:profile:user:" + userId; }
    public static String profileGuest(String guestId)  { return "rec:profile:guest:" + guestId; }
    public static String consumedGuest(String guestId) { return "rec:consumed:guest:" + guestId; }
    public static String resultUser(String userId)     { return "rec:result:user:" + userId; }
    public static String resultGuest(String guestId)   { return "rec:result:guest:" + guestId; }
}