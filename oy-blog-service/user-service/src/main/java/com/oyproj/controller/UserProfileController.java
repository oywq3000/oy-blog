package com.oyproj.controller;

import com.oyproj.common.annotation.Log;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.domain.dto.UpdateProfileDto;
import com.oyproj.domain.vo.SimpleUserVo;
import com.oyproj.domain.vo.UserPublicVo;
import com.oyproj.domain.vo.UserVo;
import com.oyproj.service.UserCommonBizService;
import com.oyproj.service.UserProfileBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户个人信息接口
 */
@Slf4j
@Tag(name = "用户个人信息控制器", description = "用户个人信息查询操作")
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class UserProfileController {
    @NotNull
    private final UserProfileBizService profileBiz;
    @NotNull private final UserCommonBizService commonBiz;

    /**
     * 获取当前登录用户信息
     *
     * @return 当前登录用户信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前登录用户信息", description = "获取当前登录用户的详细信息")
    public Result<UserVo> getProfile() {
        log.info("get profile");
        return profileBiz.getProfile();
    }

    /**
     * 获取简单用户Profile
     */

    @GetMapping("/public/simple/{userId}")
    @Operation(summary = "获取简单用户信息", description = "根据id获取简单用户的详细信息")
    public Result<SimpleUserVo> getSimpleProfile(@PathVariable String userId) {
        return profileBiz.getSimpleProfile(userId);
    }
    /**
     * 获取公共用户信息
     */
    @GetMapping("/public/{userId}")
    @Operation(summary = "获取可公开的用户信息", description = "获取当前登录用户的详细信息")
    Result<UserPublicVo> getUserPublicInfo(@PathVariable String userId){
        return profileBiz.getUserPublicInfo(userId);
    }




    /**
     * 根据用户ID获取用户名
     *
     * @param userId 用户ID
     * @return 用户名
     */
    @GetMapping("/public/username/{userId}")
    @Operation(summary = "根据用户ID获取用户名", description = "根据用户ID查询用户名")
    public Result<Map<String, String>> getUsernameById(@PathVariable @NotNull String userId) {
        return profileBiz.getUsernameById(userId);
    }


    /**
     * 根据用户id获取用户UserDTO
     */
    @GetMapping("/info/{userId}")
    @Operation(summary = "getUserDTO",description = "根据userId获取信息")
    public Result<UserDTO> getUserDTO(@PathVariable String userId){
        return profileBiz.getUserDTOById(userId);
    }

    /**
     * 上传用户头像
     *
     * @param file 头像文件
     * @return 头像URL
     */
    @PostMapping("/avatar")
    @Operation(summary = "上传用户头像", description = "上传并更新当前用户的头像")
    public Result<String> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return commonBiz.uploadAvatar(file);
    }

    /**
     * 更新用户数据
     */
    @PostMapping("/update")
    @Operation(summary = "更新用户数据",description = "跟新用户数据")
    public Result<Object> update(@RequestBody UpdateProfileDto updateProfileDto) {
        return profileBiz.update(updateProfileDto);
    }



}
