package com.oyproj.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateProfileDto {
    public String username;
    public String email;
    public String avatarUrl;
    public String bio;
    /**
     * 技能列表（排序即展示顺序）。
     * null = 本次不修改技能；空列表 = 清空全部技能。
     */
    public List<String> skills;
}
