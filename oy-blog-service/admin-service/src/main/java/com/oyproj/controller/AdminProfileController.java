package com.oyproj.controller;

import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.security.annotation.RequirePermission;
import com.oyproj.service.AdminProfileBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员信息控制器
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminProfileController {

    private final AdminProfileBizService biz;

    @GetMapping("/current-user")
    @RequirePermission("admin:base")
    public Result<UserDTO> currentUser() {
        return biz.currentUser();
    }
}
