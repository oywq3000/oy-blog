package com.oyproj.service.impl;

import com.oyproj.common.base.Result;
import com.oyproj.domain.vo.UserPublicVo;
import com.oyproj.service.UserStatService;
import org.springframework.cache.annotation.Cacheable;

import java.util.concurrent.CompletableFuture;

public class UserStatServiceImpl implements UserStatService {
    /**
     * 获取用户公开信息（带缓存）
     * @param userId 用户ID
     * @return 用户公开信息
     */
    @Override
    @Cacheable(value = "userStats",key="#userId")
    public Result<UserPublicVo> getUserPublicInfo(String userId) {
        return null;
    }

    @Override
    public void evictUserStatsCache(String userId) {

    }

    @Override
    public CompletableFuture<Void> asyncUpdateUserStatsCache(String userId) {
        return null;
    }
}
