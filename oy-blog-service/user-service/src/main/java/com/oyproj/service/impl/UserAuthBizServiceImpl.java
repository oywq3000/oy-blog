package com.oyproj.service.impl;

import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.BaseException;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.exception.ValidationException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.BeanCopyUtils;
import com.oyproj.common.utils.JwtUtil;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.dto.*;
import com.oyproj.domain.entity.User;
import com.oyproj.service.UserAuthBizService;
import com.oyproj.utils.SecurityUtil;
import io.jsonwebtoken.Claims;


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
        super(userDao,commonCache);
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
        userDTO.setBlogRole(BlogRole.READER);
        //SpringSecurity 登录
        SecurityUtil.login(userDTO,null);
        //存储对象到Redis中
        TokenInfo tokenInfo = SecurityUtil.getTokenInfo();
        //将当前信息存储到Redis中，还有一个含义代表当前用户已经登录，登出时需要把它从redis中删除
        commonCache.put(CachePrefix.USER_ID.getPrefix() + userDTO.getId(), userDTO, tokenInfo.getExpiresIn());
        //存储 refresh token 到 Redis，用于刷新时校验（旋转策略）
        commonCache.put(CachePrefix.REFRESH_TOKEN.getPrefix() + userDTO.getId(),
                tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpiresIn());
        return Result.ok(tokenInfo);
    }

    @Override
    public Result<Object> register(RegisterDto req) {
        //todo 做注册判断
        User userByName = userDao.getUserByName(req.getUsername());
        if(userByName!=null){
            throw new BaseException(ResultCode.USERNAME_DUPLICATE);
        }
        // 邮箱查重（email 列有唯一索引，不查重会触发 DB 异常变 500）
        if (userDao.getByEmail(req.getEmail()) != null) {
            throw new BaseException(ResultCode.EMAIL_DUPLICATE);
        }
        // 校验邮箱验证码（5 分钟有效，比对成功后删除防重用）
        String codeKey = CachePrefix.EMAIL_VERIFY_CODE.getPrefix() + req.getEmail();
        String cachedCode = commonCache.getString(codeKey);
        if (cachedCode == null || !cachedCode.equals(req.getEmailCode())) {
            throw new ValidationException(I18n("email.code.invalid"));
        }
        commonCache.remove(codeKey);

        User user = User.builder()
                .id(getId())
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .email(req.getEmail())
                .lastLoginIp(req.getIpAddress())
                .status(1)
                .emailVerified(1)
                .emailVerifiedAt(LocalDateTime.now())
                .build();
        userDao.save(user);
        return Result.ok();
    }

    @Override
    public Result<TokenInfo> refresh(String refreshToken) {
        // 1. 解析 refresh token，验证签名和过期
        Claims claims;
        try {
            claims = JwtUtil.parseToken(refreshToken);
        } catch (Exception e) {
            return Result.error("刷新令牌无效或已过期");
        }

        // 2. 校验 token 类型必须为 refresh
        String tokenType = JwtUtil.getTokenType(claims);
        if (!JwtUtil.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            return Result.error("令牌类型错误，需要刷新令牌");
        }

        String userId = claims.getSubject();

        // 3. 对比 Redis 中存储的 refresh token（防重放，实现旋转策略）
        String storedToken = commonCache.getString(CachePrefix.REFRESH_TOKEN.getPrefix() + userId);
        if (storedToken == null) {
            return Result.error("刷新令牌已失效，请重新登录");
        }
        if (!storedToken.equals(refreshToken)) {
            // 令牌不匹配，可能被重放攻击，清除已失效的 token
            commonCache.remove(CachePrefix.REFRESH_TOKEN.getPrefix() + userId);
            commonCache.remove(CachePrefix.USER_ID.getPrefix() + userId);
            return Result.error("刷新令牌无效，请重新登录");
        }

        // 4. 生成新 token（旋转：旧 refresh token 立即失效）
        String newAccessToken = JwtUtil.generateAccessToken(userId);
        String newRefreshToken = JwtUtil.generateRefreshToken(userId);

        // 5. 更新 Redis
        // 续期 session；若会话已随 access token 一起过期，则从 DB 重建，
        // 否则网关对刷新后的新 token 永远认证失败（hasKey 检查），前端会死循环
        Object session = commonCache.get(CachePrefix.USER_ID.getPrefix() + userId);
        if (session == null) {
            User user = userDao.getById(userId);
            if (user == null) {
                // 用户已不存在，无法恢复会话 —— 拒绝刷新，让前端走"重新登录"
                return Result.error("用户不存在或已注销，请重新登录");
            }
            UserDTO userDTO = new UserDTO();
            BeanCopyUtils.copyProperties(user, userDTO);
            userDTO.setBlogRole(BlogRole.READER);
            session = userDTO;
        }
        commonCache.put(CachePrefix.USER_ID.getPrefix() + userId,
                session,
                JwtUtil.getAccessTokenExpireTime());
        // 替换 refresh token
        commonCache.put(CachePrefix.REFRESH_TOKEN.getPrefix() + userId,
                newRefreshToken, JwtUtil.getRefreshTokenExpireTime());

        // 6. 返回新 TokenInfo
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setAccessToken(newAccessToken);
        tokenInfo.setTokenType("Bearer");
        tokenInfo.setExpiresIn(JwtUtil.getAccessTokenExpireTime());
        tokenInfo.setRefreshToken(newRefreshToken);
        tokenInfo.setRefreshTokenExpiresIn(JwtUtil.getRefreshTokenExpireTime());
        tokenInfo.setUserId(userId);
        return Result.ok(tokenInfo);
    }

    @Override
    public Result<Object> logout() {
        String userId = getCurrentUserId();
        commonCache.remove(CachePrefix.REFRESH_TOKEN.getPrefix() + userId); // 清除 refresh token
        commonCache.remove(CachePrefix.USER_ID.getPrefix() + userId); // 移除 login 操作
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

    @Override
    public Result<Object> resetPassword(ResetPasswordDto req) {
        // 1. 邮箱必须已注册
        User user = userDao.getByEmail(req.getEmail());
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }

        // 2. 两次密码一致（先于验证码比对，避免误消耗验证码）
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ValidationException(I18n("password.mismatch"));
        }

        // 3. 校验邮箱验证码（5 分钟有效，比对成功后删除防重用）
        String codeKey = CachePrefix.EMAIL_RESET_CODE.getPrefix() + req.getEmail();
        String cachedCode = commonCache.getString(codeKey);
        if (cachedCode == null || !cachedCode.equals(req.getEmailCode())) {
            throw new ValidationException(I18n("email.code.invalid"));
        }
        commonCache.remove(codeKey);

        // 4. 加密新密码并落库
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userDao.updateById(user);

        // 5. 清除该用户全部旧会话（与 logout 一致），重置后旧 token 全部失效
        commonCache.remove(CachePrefix.REFRESH_TOKEN.getPrefix() + user.getId());
        commonCache.remove(CachePrefix.USER_ID.getPrefix() + user.getId());
        return Result.ok(I18n("password.reset.success"));
    }

    @Override
    public Result<String> test() {
        return Result.ok(getCurrentUserId());
    }


}
