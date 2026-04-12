package com.oyproj.service;

import com.oyproj.common.base.Result;
import com.oyproj.domain.vo.UserPublicVo;

import java.util.concurrent.CompletableFuture;

public interface UserStatService {
    //得到公开的用户状态信息
    Result<UserPublicVo> getUserPublicInfo(String userId);
    //清除缓存
    public void evictUserStatsCache(String userId);

    //异步跟新缓存
    public CompletableFuture<Void> asyncUpdateUserStatsCache(String userId);
}
