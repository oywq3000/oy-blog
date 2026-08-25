package com.oyproj.common.security.interceptor;

import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.security.annotation.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 权限拦截器：校验请求头 X-User-Type 是否为 ADMIN。
 * 为什么读请求头而不是 SecurityContext：
 * 下游服务被 Feign 调用时 X-Service-Call=true 会短路 AuthFilter 身份构建，
 * 而网关在每次转发时都会用 Redis 中的真实角色覆盖 X-User-Type 头，直接读头是各场景下都可靠且最简单的判断。
 */
@Aspect
@Component
public class RequirePermissionInterceptor {

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String userType = attrs.getRequest().getHeader(HeaderConstant.USER_TYPE.getValue());
            if (BlogRole.ADMIN.name().equals(userType)) {
                return joinPoint.proceed();
            }
        }
        throw new ForbiddenException();
    }
}
