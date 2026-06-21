package com.oyproj.common.security.filter;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.security.domain.SecurityUser;
import com.oyproj.common.service.CommonCache;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class AuthFilter implements Filter {

    private final CommonCache commonCache;
    public AuthFilter(CommonCache commonCache){
        this.commonCache = commonCache;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String serviceCallHeader = httpServletRequest.getHeader("X-Service-Call");
        if("true".equals(serviceCallHeader)){
            // 这里可以设置一个特殊的服务用户身份
            SecurityUser serviceUser = new SecurityUser("service",1,null);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    serviceUser, null,null);
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
            return;
        }

        //非服务器之间调用
        String userType = httpServletRequest.getHeader(HeaderConstant.USER_TYPE.getValue());
        BlogRole blogRole = BlogRole.valueOf(userType);
        String userId = httpServletRequest.getHeader(HeaderConstant.USER_ID.getValue());
        UserDTO userDTO;
        switch (blogRole){
            case READER -> {
                // Gateway 已验证 JWT 和会话，从缓存获取完整 UserDTO，若缓存失效则用 header 兜底
                userDTO = (UserDTO) commonCache.get(CachePrefix.USER_ID.getPrefix() + userId);
                if (userDTO == null) {
                    userDTO = new UserDTO();
                    userDTO.setId(userId);
                    userDTO.setBlogRole(blogRole);
                }
            }
            case GUEST -> {
                userDTO = new UserDTO();
                userDTO.setId(userId);
                userDTO.setStatus(2); //代表游客状态
                userDTO.setBlogRole(blogRole);
                userDTO.setUsername("游客");
            }
            case ADMIN -> {
                userDTO = new UserDTO();
                userDTO.setId(userId);
                userDTO.setBlogRole(blogRole);
            }
            default -> {
                throw new UnAuthorizedException("未知用户类型");
            }
        }
        //todo 完成权限绑定功能
        List<GrantedAuthority> auths = new ArrayList<>();
        SecurityUser securityUser = new SecurityUser(userDTO, auths);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request,response);
    }
}
