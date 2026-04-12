package com.oyproj.domain.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserPublicVo implements Serializable {
    private String name;
    private String avatar;
    private String bio;
    private Integer articleCount;
    private Integer favoriteCount;
    private Integer likeCount;
}
