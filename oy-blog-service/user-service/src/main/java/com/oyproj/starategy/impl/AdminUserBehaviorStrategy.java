package com.oyproj.starategy.impl;

import com.oyproj.base.UserBizBase;
import com.oyproj.common.component.IpParseApi;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.entity.User;
import com.oyproj.domain.vo.UserVo;
import com.oyproj.starategy.UserBehaviorStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AdminUserBehaviorStrategy extends UserBizBase implements UserBehaviorStrategy {
    private final IpParseApi ipParseApi;
    private final UserDao userDao;

    public AdminUserBehaviorStrategy(UserDao userDao,
                                     CommonCache cache,
                                     IpParseApi ipParseApi) {
        super(userDao, cache);
        this.ipParseApi = ipParseApi;
        this.userDao = userDao;
    }

    @Override
    public BlogRole supports() {
        return BlogRole.ADMIN;
    }

    @Override
    public UserVo getProfile() {
        User user = userDao.getById(getCurrentUserId());
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }

        UserVo userVo = UserVo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .status(user.getStatus())
                .blogRole(getCurrentUserDTO().getBlogRole())
                .lastLogin(user.getLastLoginAt())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(Integer.valueOf(1).equals(user.getEmailVerified()))
                .createdAt(user.getCreatedAt())
                .build();
        try{
            String ipAddress = ipParseApi.parseIpAddress(user.getLastLoginIp()).getRegion();
            userVo.setIpAddress(ipAddress.isEmpty() ? I18n("common.unknown") : ipAddress);
        }catch (IOException e){
            userVo.setIpAddress(I18n("common.unknown"));
        }
        return userVo;
    }
}
