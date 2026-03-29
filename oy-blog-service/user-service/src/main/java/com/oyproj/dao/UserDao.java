package com.oyproj.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.oyproj.domain.entity.Permission;
import com.oyproj.domain.entity.Role;
import com.oyproj.domain.entity.User;


import java.util.List;

/**
 * 用户数据查询接口
 */
public interface UserDao extends IService<User> {
    /**
     * 根据用户名获取用户信息
     */
    User getUserByName(String username);
    /**
     * 根据用户ID获取用户角色列表
     *
     * @return 用户角色列表
     */
    List<User> getAllUsers();

    /**
     * 根据用户ID判断用户是否启用
     *
     * @param id 用户ID
     * @return 如果用户启用则返回true，否则返回false
     */
    Boolean isEnabledById(String id);

    /**
     * 根据邮箱获取用户信息
     *
     * @param email 邮箱
     * @return 用户信息
     */
    User getByEmail(String email);

    /**
     * 更新用户最后登录时间与IP
     * @param id 用户ID
     * @param ip 登录IP
     */
    void updateLastLogin(String id, String ip);

    /**
     * 查询用户的角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> listRolesByUserId(String userId);

    /**
     * 查询用户的有效权限列表（合并角色继承与直授，剔除直拒）
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    List<Permission> listPermissionsByUserId(String userId);
}
