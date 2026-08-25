package com.oyproj.api.user.domain.dto;

import lombok.Data;

@Data
public class UserAdminPageDto {
    private Integer page = 1;
    private Integer size = 10;
    /** 用户名/邮箱模糊搜索，null=不限 */
    private String keyword;
    /** 0=禁用 1=启用，null=全部 */
    private Integer status;
}
