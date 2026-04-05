package com.oyproj.domain.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户视图对象
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVo {
    /**
     * 用户ID
     */
    @Schema(description = "用户ID", example = "123456")
    private String id;

    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "user123")
    private String username;

    /**
     * bio
     */
    @Schema(description = "各人简介")
    private String bio;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱", example = "user123@example.com")
    private String email;

    /**
     * 状态
     */
    @Schema(description = "状态", example = "1")
    private Integer status;

    /**
     * 头像URL
     */
    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;



    /**
     * 邮箱是否验证
     */
    @Schema(description = "邮箱是否验证", example = "true")
    private Boolean emailVerified;

    /**
     * 用户最后登录IP地址
     */
    @Schema(description = "用户最后登录IP地址", example = "192.168.1.1")
    private String ipAddress;

    /**
     * 用户最后登录时间
     */
    @Schema(description = "用户最后登录时间", example = "2025-12-01T12:00:00")
    private LocalDateTime lastLogin;

    /**
     * 设置用户最后登录IP地址
     * @param ipAddress 用户最后登录IP地址
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
