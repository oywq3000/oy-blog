package com.oyproj.filter;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.utils.JsonUtil;
import com.oyproj.domain.dto.SecurityUser;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        //从请求头中获取信息
        String userJson = httpServletRequest.getHeader(HeaderConstant.USER_ID.getValue());
        if(userJson!=null){
        //使用工具类将JSON字符串转为Map
            UserDTO userDTO = JsonUtil.parseToUserDTO(userJson);
            //todo 完成权限绑定功能
            List<GrantedAuthority> auths = new ArrayList<>();
            SecurityUser securityUser = new SecurityUser(userDTO, auths);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(securityUser,null,securityUser.getAuthorities());
            authentication.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request,response);
    }
}
