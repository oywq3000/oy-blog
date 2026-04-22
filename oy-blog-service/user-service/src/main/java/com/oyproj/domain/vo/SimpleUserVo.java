package com.oyproj.domain.vo;

import lombok.Data;

/**
 * 简单返回用户信息，用于评论文章等
 */
@Data
public class SimpleUserVo {
    //目前只包含名子和头像信息
    private String name;
    private String avatar;
}