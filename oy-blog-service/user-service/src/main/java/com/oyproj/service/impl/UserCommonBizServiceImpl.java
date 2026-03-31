package com.oyproj.service.impl;

import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.dao.UserDao;
import com.oyproj.service.UserCommonBizService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserCommonBizServiceImpl extends UserBizBase implements UserCommonBizService {
    public UserCommonBizServiceImpl(UserDao userDao) {
        super(userDao);
    }

    @Override
    public Result<String> uploadAvatar(MultipartFile file) {
        return null;
    }
}
