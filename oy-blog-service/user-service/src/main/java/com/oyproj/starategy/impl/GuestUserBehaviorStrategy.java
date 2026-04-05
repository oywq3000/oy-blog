package com.oyproj.starategy.impl;
import com.oyproj.base.UserBizBase;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.service.CommonCache;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.vo.UserVo;
import com.oyproj.starategy.UserBehaviorStrategy;
import org.springframework.stereotype.Component;

@Component
public class GuestUserBehaviorStrategy extends UserBizBase implements UserBehaviorStrategy {
    public GuestUserBehaviorStrategy(UserDao userDao, CommonCache cache) {
        super(userDao, cache);
    }

    @Override
    public BlogRole supports() {
        return BlogRole.GUEST;
    }

    @Override
    public UserVo getProfile() {
        UserDTO currentUserDTO = getCurrentUserDTO();
        UserVo userVo = UserVo.builder()
                .id(currentUserDTO.getId())
                .username(currentUserDTO.getUsername())
                .status(currentUserDTO.getStatus())
                .avatarUrl(currentUserDTO.getAvatarUrl())
                .build();
        return userVo;
    }

}
