package com.oyproj.api.config;

import com.oyproj.common.constant.HeaderConstant;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 管理端 Feign 配置：把管理员的 X-User-Id/X-User-Type 透传给下游服务，
 * 下游管理接口的 @RequirePermission 依据该头放行。
 * 注意：本类不能加 @Configuration，否则会污染所有 Feign 客户端；
 * 由 Admin*Client 通过 configuration 属性显式引用。
 */
public class AdminFeignConfig {

    @Bean
    public RequestInterceptor adminIdentityPropagationInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs == null) {
                    return;
                }
                String userId = attrs.getRequest().getHeader(HeaderConstant.USER_ID.getValue());
                String userType = attrs.getRequest().getHeader(HeaderConstant.USER_TYPE.getValue());
                if (userId != null) {
                    template.header(HeaderConstant.USER_ID.getValue(), userId);
                }
                if (userType != null) {
                    template.header(HeaderConstant.USER_TYPE.getValue(), userType);
                }
            }
        };
    }
}
