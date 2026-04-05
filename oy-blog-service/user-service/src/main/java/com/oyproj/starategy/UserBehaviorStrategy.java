package com.oyproj.starategy;

import com.oyproj.common.constant.BlogRole;
import com.oyproj.domain.vo.UserVo;

public interface UserBehaviorStrategy {
    BlogRole supports();
    UserVo getProfile();
}