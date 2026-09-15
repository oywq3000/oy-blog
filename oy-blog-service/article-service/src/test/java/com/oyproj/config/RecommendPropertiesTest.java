package com.oyproj.config;

import com.oyproj.common.RecommendKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RecommendProperties 默认值")
class RecommendPropertiesTest {

    @Test
    void defaults_satisfyColdStartAndGuestTtl() {
        RecommendProperties p = new RecommendProperties();
        assertEquals(30, p.getProfileTopTags());
        assertEquals(100, p.getResultCacheSize());
        assertEquals(600L, p.getResultCacheTtlSeconds());
        assertEquals(604800L, p.getGuestTtlSeconds());
    }

    @Test
    void keys_areStableAndNamespaced() {
        assertEquals("rec:profile:user:u1", RecommendKeys.profileUser("u1"));
        assertEquals("rec:profile:guest:g1", RecommendKeys.profileGuest("g1"));
        assertEquals("rec:consumed:guest:g1", RecommendKeys.consumedGuest("g1"));
        assertEquals("rec:result:user:u1", RecommendKeys.resultUser("u1"));
        assertEquals("rec:result:guest:g1", RecommendKeys.resultGuest("g1"));
    }
}