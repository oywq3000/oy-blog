package com.oyproj.service.impl;

import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.BaseException;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.exception.ValidationException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.BeanCopyUtils;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.dto.*;
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
    private final CommonCache commonCache;
    private final PasswordEncoder passwordEncoder;
    public UserAuthBizServiceImpl(PasswordEncoder passwordEncoder,UserDao userDao,CommonCache commonCache) {
        super(userDao);
        this.passwordEncoder = passwordEncoder;
        this.commonCache = commonCache;
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
        //创建服务间通用的
        UserDTO userDTO = new UserDTO();
        BeanCopyUtils.copyProperties(user,userDTO);
        
        //SpringSecurity 登录
        SecurityUtil.login(userDTO,null);
        //存储对象到Redis中

        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();

        //将当前信息存储到Redis中
        commonCache.put(userDTO.getId(),userDTO);
        commonCache.put(CachePrefix.TOKEN.getPrefix()+userDTO.getId()+tokenInfo.getAccessToken(),
                1,tokenInfo.getExpiresIn());
        commonCache.put(CachePrefix.TOKEN.getPrefix()+userDTO.getId()+tokenInfo.getRefreshToken(),
                1,tokenInfo.getRefreshTokenExpiresIn());

        return Result.ok(tokenInfo);
    }

    @Override
    public Result<Object> register(RegisterDto req) {
        //todo 做注册判断
        User userByName = userDao.getUserByName(req.getUsername());
        if(userByName!=null){
            throw new BaseException(ResultCode.USERNAME_DUPLICATE);
        }
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

        User user = null/* = userDao.getById(getUserId())*/;
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
