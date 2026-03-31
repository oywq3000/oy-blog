package com.oyproj.controller;

import com.oyproj.service.UserCommonBizService;
import com.oyproj.service.UserProfileBizService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户个人信息接口
 */
@Tag(name = "用户个人信息控制器", description = "用户个人信息查询操作")
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class UserProfileController {
    @NotNull
    private final UserProfileBizService profileBiz;
    @NotNull private final UserCommonBizService commonBiz;
}
