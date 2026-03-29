package com.oyproj.base;

import com.oyproj.common.service.BaseBiz;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

/**
 * 基础业务服务类
 */

@Getter
@RequiredArgsConstructor
public class UserBizBase extends BaseBiz {
    protected final UserDao userDao;
    @Value("${app.host:http://localhost:8080}")
    protected String appHost;
    /**
     * 根据用户名或邮箱获取用户
     *
     * @param key 用户名或邮箱
     * @return 用户实体
     */
    public User getUser(String key) {
        User user = userDao.getUserByName(key);
        if (user != null) {
            return user;
        }
        return userDao.getByEmail(key);
    }
}
