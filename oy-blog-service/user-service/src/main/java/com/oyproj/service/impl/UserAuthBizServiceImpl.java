package com.oyproj.service.impl;

import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.exception.ValidationException;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.dto.LoginDto;
import com.oyproj.domain.dto.RegisterDto;
import com.oyproj.domain.dto.TokenInfo;
import com.oyproj.domain.dto.UpdatePasswordDto;
import com.oyproj.domain.entity.User;
import com.oyproj.service.UserAuthBizService;
import com.oyproj.utils.SecurityUtil;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


/**
 * 用户认证业务实现类
 */
@Service
public class UserAuthBizServiceImpl extends UserBizBase implements UserAuthBizService {
    private final PasswordEncoder passwordEncoder;
    public UserAuthBizServiceImpl(PasswordEncoder passwordEncoder,UserDao userDao) {
        super(userDao);
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户登录
     *
     * @param req 登录请求参数
     * @return 登录结果
     */
    @Override
    public Result<TokenInfo> login(LoginDto req) {
        User user = getUser(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new ValidationException(I18n("user.login.invalid"));
        }
        if (user.getStatus() == 0) {
            throw new ForbiddenException(I18n("user.disabled"));
        }
        if (user.getLastLoginIp() != null && !user.getLastLoginIp().equals(req.getIpAddress())) {
            user.setLastLoginIp(req.getIpAddress());
        }
        user.setLastLoginAt(LocalDateTime.now());
        userDao.updateById(user);
        SecurityUtil.login(user.getId());
        return Result.ok(SecurityUtil.getTokenInfo());
    }

    @Override
    public Result<Object> register(RegisterDto req) {
        //todo 做注册判断
        User user = User.builder()
                .id(getId())
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .email(req.getEmail())
                .lastLoginIp(req.getIpAddress())
                .status(1)
                .build();
        userDao.save(user);
        return Result.ok();
    }

    @Override
    public Result<Object> logout() {
        SecurityUtil.logout();
        return Result.ok();
    }

    @Override
    public Result<Object> updatePassword(UpdatePasswordDto req) {

        User user = userDao.getById(getUserId());
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new ValidationException(I18n("password.old.invalid"));
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ValidationException(I18n("password.mismatch"));
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userDao.updateById(user);
        return Result.ok();
    }
}
