package com.oyproj.service.impl;

import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.BaseException;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.exception.ValidationException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.dto.EmailCodeSendDto;
import com.oyproj.domain.entity.User;
import com.oyproj.service.EmailSendService;
import com.oyproj.service.EmailVerifyBizService;
import com.oyproj.utils.VerifyCodeUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 邮箱验证业务实现：注册验证码 + 验证链接
 *
 * <p>验证码：6 位数字，Redis 存 {EMAIL_VERIFY_CODE}_&lt;email&gt;，5 分钟有效；
 * 60 秒内同一邮箱最多发 1 次、每日上限 10 次（incr 计数器）。
 * 验证链接：随机 token 存 {EMAIL_VERIFY_TOKEN}_&lt;token&gt;=userId，24 小时有效，单次使用。</p>
 */
@Service
public class EmailVerifyBizServiceImpl extends UserBizBase implements EmailVerifyBizService {

    /** 验证码有效期（秒） */
    private static final long CODE_TTL_SECONDS = 300L;
    /** 验证链接有效期（秒） */
    private static final long TOKEN_TTL_SECONDS = 86400L;
    /** 同一邮箱两次发送的最小间隔（秒） */
    private static final long SEND_INTERVAL_SECONDS = 60L;
    /** 同一邮箱每日发送上限 */
    private static final long DAILY_SEND_LIMIT = 10L;

    private final EmailSendService emailSendService;

    public EmailVerifyBizServiceImpl(UserDao userDao, CommonCache cache, EmailSendService emailSendService) {
        super(userDao, cache);
        this.emailSendService = emailSendService;
    }

    @Override
    public Result<Object> sendCode(EmailCodeSendDto dto) {
        String email = dto.getEmail();

        // 1. 邮箱必须未被注册
        //if (userDao.getByEmail(email) != null) {
        //    throw new BaseException(ResultCode.EMAIL_DUPLICATE);
       // }

        // 2. 防刷：60 秒内最多 1 次 + 每日上限
        String sendKey = CachePrefix.EMAIL_VERIFY_CODE.getPrefix() + "SEND_" + email;
        String dailyKey = CachePrefix.EMAIL_VERIFY_CODE.getPrefix() + "DAILY_" + email;
        if (cache.incr(sendKey, SEND_INTERVAL_SECONDS) > 0) {
            throw new ValidationException(I18n("email.code.send.frequent"));
        }
        if (cache.incr(dailyKey, 86400L) >= DAILY_SEND_LIMIT) {
            throw new ValidationException(I18n("email.code.send.limit"));
        }

        // 3. 生成验证码并缓存 5 分钟
        String code = VerifyCodeUtils.genCode();
        cache.put(CachePrefix.EMAIL_VERIFY_CODE.getPrefix() + email, code, CODE_TTL_SECONDS);

        // 4. 发送邮件
        emailSendService.sendVerifyCode(email, code);
        return Result.ok(I18n("email.code.sent"));
    }

    @Override
    public Result<Object> request() {
        // 仅登录用户（READER）可请求验证邮件；游客直接拒绝
        if (getCurrentUserBlogType() != BlogRole.READER) {
            throw new ValidationException(I18n("email.verify.needLogin"));
        }
        String userId = getCurrentUserId();
        User user = userDao.getById(userId);
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }
        if (Integer.valueOf(1).equals(user.getEmailVerified())) {
            throw new ValidationException(I18n("email.verify.alreadyVerified"));
        }

        // 生成 token 缓存 24 小时，发链接邮件
        String token = VerifyCodeUtils.genToken();
        cache.put(CachePrefix.EMAIL_VERIFY_TOKEN.getPrefix() + token, userId, TOKEN_TTL_SECONDS);

        String verifyUrl = (appHost == null ? "" : appHost) + "/email/verify?token=" + token;
        emailSendService.sendVerifyLink(user.getEmail(), verifyUrl, user.getUsername());
        return Result.ok();
    }

    @Override
    public Result<Object> confirm(String token) {
        String userId = cache.getString(CachePrefix.EMAIL_VERIFY_TOKEN.getPrefix() + token);
        if (userId == null) {
            throw new ValidationException(I18n("email.verify.tokenInvalid"));
        }
        User user = userDao.getById(userId);
        if (user == null) {
            throw new NotFoundException(I18n("user.notfound"));
        }
        user.setEmailVerified(1);
        user.setEmailVerifiedAt(LocalDateTime.now());
        userDao.updateById(user);
        cache.remove(CachePrefix.EMAIL_VERIFY_TOKEN.getPrefix() + token); // 单次使用
        return Result.ok(I18n("email.verify.success"));
    }

    @Override
    public Result<Boolean> status() {
        if (getCurrentUserBlogType() != BlogRole.READER) {
            return Result.ok(false);
        }
        User user = userDao.getById(getCurrentUserId());
        return Result.ok(user != null && Integer.valueOf(1).equals(user.getEmailVerified()));
    }
}
