package com.oyproj.service.impl;

import com.oyproj.api.article.client.ArticleControllerClient;
import com.oyproj.api.article.domain.UserArticleStatDto;
import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.component.IpParseApi;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.dto.UpdateProfileDto;
import com.oyproj.domain.entity.User;
import com.oyproj.domain.vo.SimpleUserVo;
import com.oyproj.domain.vo.UserPublicVo;
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
    @NotNull private final ArticleControllerClient articleControllerClient;
    public UserProfileBizServiceImpl(UserDao userDao,
                                     IpParseApi ipParseApi,
                                     CommonCache commonCache,
                                     UserBehaviorStrategyFactory userBehaviorStrategyFactory,
                                     ArticleControllerClient articleControllerClient) {
        super(userDao,commonCache);
        this.ipParseApi =ipParseApi;
        this.userBehaviorStrategyFactory = userBehaviorStrategyFactory;
        this.articleControllerClient = articleControllerClient;
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

    @Override
    public Result<UserDTO> getUserDTOById(String userId) {
        User user = userDao.getById(userId);
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }
        UserDTO userDTO = new UserDTO();
        copyProperties(user,userDTO);
        return Result.ok(userDTO);
    }

    @Override
    public Result<UserPublicVo> getUserPublicInfo(String userId) {
        User user = userDao.getById(userId);
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }
        UserPublicVo userPublicVo = new UserPublicVo();
        userPublicVo.setName(user.getUsername());
        userPublicVo.setAvatar(user.getAvatarUrl());
        userPublicVo.setBio(user.getBio());
        Result<UserArticleStatDto> userStats = articleControllerClient.getUserStats(userId);
        if(userStats.getIsSuccess()&&userStats.getData()!=null){
            userPublicVo.setArticleCount(userStats.getData().getArticleCount());
            userPublicVo.setLikeCount(userStats.getData().getLikeCount());
            userPublicVo.setFavoriteCount(userStats.getData().getFavoriteCount());
        }
        return Result.ok(userPublicVo);
    }

    @Override
    public Result<SimpleUserVo> getSimpleProfile(String userId) {
        User user = userDao.getById(userId);
        SimpleUserVo simpleUserVo = new SimpleUserVo();
        simpleUserVo.setName(user.getUsername());
        simpleUserVo.setAvatar(user.getAvatarUrl());
        return Result.ok(simpleUserVo);
    }
}
