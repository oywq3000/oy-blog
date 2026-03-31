package com.oyproj.service.impl;

import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.component.IpParseApi;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.entity.User;
import com.oyproj.domain.vo.UserVo;
import com.oyproj.service.UserProfileBizService;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * 用户个人信息服务实现
 */
@Service
public class UserProfileBizServiceImpl extends UserBizBase implements UserProfileBizService {

    @NotNull private final IpParseApi ipParseApi;
    public UserProfileBizServiceImpl(UserDao userDao,IpParseApi ipParseApi) {
        super(userDao);
        this.ipParseApi =ipParseApi;
    }
    @Override
    public Result<UserVo> getProfile() {
        User user = userDao.getById(getUserId());
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }
        UserVo userVo = UserVo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .status(user.getStatus())
                .lastLogin(user.getLastLoginAt())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(Integer.valueOf(1).equals(user.getEmailVerified()))
                .build();
        try{
            String ipAddress = ipParseApi.parseIpAddress(user.getLastLoginIp()).getRegion();
            userVo.setIpAddress(ipAddress.isEmpty() ? I18n("common.unknown") : ipAddress);
        }catch (IOException e){
            userVo.setIpAddress(I18n("common.unknown"));
        }
        return Result.ok(userVo);
    }

    @Override
    public Result<Map<String, String>> getUsernameById(String userId) {
        User user = userDao.getById(userId);
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }
        return Result.ok(Map.of("username", user.getUsername()));
    }
}
