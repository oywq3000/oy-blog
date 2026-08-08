package com.oyproj.api.user.client;

import com.oyproj.api.user.client.fallback.UserClientFallbackFactory;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 用户服务 Feign 客户端
 */
@FeignClient(value = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {
    
    /**
     * 根据用户ID获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/profile/info/{userId}")
    Result<UserDTO> getUserDTO(@PathVariable("userId") String userId);

    /**
     * 批量获取用户信息
     * @param userIds 用户ID列表
     * @return 用户信息列表
     */
    @PostMapping("/profile/info/batch")
    Result<List<UserDTO>> getUserDTOs(@RequestBody List<String> userIds);
}