package com.oyproj.base;

import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.security.domain.SecurityUser;
import com.oyproj.common.service.base.BaseBiz;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * admin-service 业务基类
 */
public class AdminBizBase extends BaseBiz {

    //获得当前用户id
    public String getCurrentUserId() {
        SecurityUser securityUser = (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return securityUser.getUsername();
    }

    //获得当前用户
    public UserDTO getCurrentUserDTO() {
        return ((SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
    }
}
