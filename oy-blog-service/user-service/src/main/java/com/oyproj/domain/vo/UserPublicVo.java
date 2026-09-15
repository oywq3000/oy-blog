package com.oyproj.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserPublicVo implements Serializable {
    private String name;
    private String avatar;
    private String bio;
    /** 加入时间（注册时间），他人主页展示用 */
    private LocalDateTime createdAt;
    private Integer articleCount;
    private Integer favoriteCount;
    private Integer likeCount;
    /** 用户技能列表（按用户摆放顺序），无技能为空列表 */
    private List<String> skills;
}
