package com.oyproj.common.domain.dto;

import com.oyproj.common.constant.BlogRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Service服务中User类的精简类，负责在不同服务中传递用户信息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO implements Serializable {
    private String id;
    private String username;
    private Integer status;
    private String avatarUrl;
    private BlogRole blogRole;

    public UserDTO(String id,Integer status,BlogRole blogRole){
        this.id = id;
        this.status = status;
        this.blogRole = blogRole;
    }

}