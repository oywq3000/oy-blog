package com.oyproj.service.impl;

import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.component.IpParseApi;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.dto.UpdateProfileDto;
import com.oyproj.domain.entity.User;
import com.oyproj.domain.vo.UserVo;
import com.oyproj.service.UserProfileBizService;
import com.oyproj.starategy.UserBehaviorStrategy;
import com.oyproj.starategy.factory.UserBehaviorStrategyFactory;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * 用户个人信息服务实现
 */
@Service
public class UserProfileBizServiceImpl extends UserBizBase implements UserProfileBizService {

    @NotNull private final UserBehaviorStrategyFactory userBehaviorStrategyFactory;
    @NotNull private final IpParseApi ipParseApi;
    public UserProfileBizServiceImpl(UserDao userDao,
                                     IpParseApi ipParseApi,
                                     CommonCache commonCache,
                                     UserBehaviorStrategyFactory userBehaviorStrategyFactory) {
        super(userDao,commonCache);
        this.ipParseApi =ipParseApi;
        this.userBehaviorStrategyFactory = userBehaviorStrategyFactory;
    }
    @Override
    public Result<UserVo> getProfile() {
        UserBehaviorStrategy strategy = userBehaviorStrategyFactory.getStrategy(getCurrentUserBlogType());
        UserVo profile = strategy.getProfile();
        return Result.ok(profile);
    }

    @Override
    public Result<Map<String, String>> getUsernameById(String userId) {
        User user = userDao.getById(userId);
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }
        return Result.ok(Map.of("username", user.getUsername()));
    }

    @Override
    public Result<Object> update(UpdateProfileDto updateProfileDto) {
        User user = new User();
        user.setId(getCurrentUserId());
        user.setEmail(updateProfileDto.getEmail());
        user.setUsername(updateProfileDto.getUsername());
        user.setAvatarUrl(updateProfileDto.getAvatarUrl());
        user.setBio(updateProfileDto.getBio());
        return userDao.updateById(user)?Result.ok():Result.error();
    }
}
