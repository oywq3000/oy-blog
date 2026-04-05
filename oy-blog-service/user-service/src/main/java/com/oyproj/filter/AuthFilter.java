package com.oyproj.filter;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.constant.CommonConstant;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.service.CommonCache;
import com.oyproj.common.utils.JsonUtil;
import com.oyproj.domain.dto.SecurityUser;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.chrono.HijrahEra;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class AuthFilter implements Filter {

    private final CommonCache commonCache;
    public AuthFilter(CommonCache commonCache){
        this.commonCache = commonCache;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        //从请求头中获取信息
        String userType = httpServletRequest.getHeader(HeaderConstant.USER_TYPE.getValue());
        BlogRole blogRole = BlogRole.valueOf(userType);
        String userId = httpServletRequest.getHeader(HeaderConstant.USER_ID.getValue());
        UserDTO userDTO = new UserDTO();
        userDTO.setBlogRole(blogRole);
        switch (blogRole){
            case USER -> {
                userDTO =  (UserDTO) commonCache.get(CachePrefix.USER_ID.getPrefix()+userId);
            }
            case GUEST -> {
                userDTO.setId(userId);
                userDTO.setStatus(1);
            }
            case ADMIN -> {

            }
            default -> {
                throw new UnAuthorizedException("未知用户类型");
            }
        }
        if(userDTO!=null){
            //todo 完成权限绑定功能
            List<GrantedAuthority> auths = new ArrayList<>();
            SecurityUser securityUser = new SecurityUser(userDTO.getId(),userDTO.getStatus(),auths);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(securityUser,null,securityUser.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request,response);
    }
}
