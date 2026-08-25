package com.oyproj.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端权限注解。
 * MVP 实现：仅校验请求头 X-User-Type = ADMIN（网关保证该头不可伪造）；
 * 预留：value 为权限码常量（如 admin:article:write），将来接入 Permission/RolePermission 表做细粒度校验。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /** 权限码，如 admin:article:write */
    String value();
}
