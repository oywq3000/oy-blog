package com.oyproj.api.user.client.fallback;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.domain.dto.UserDTO;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    
    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override
            public Result<UserDTO> getUserDTO(String userId) {
                log.warn("用户服务调用失败，用户ID: {}, 错误: {}", userId, cause.getMessage());
                return Result.error(ResultCode.SERVICE_UNAVAILABLE.getErrCode(), "用户服务暂时不可用");
            }
        };
    }
}