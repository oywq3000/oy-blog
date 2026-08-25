package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.user.domain.dto.UserAdminPageDto;
import com.oyproj.api.user.domain.dto.UserRoleAssignDto;
import com.oyproj.api.user.domain.vo.UserAdminItemVo;
import com.oyproj.base.UserBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.constant.CachePrefix;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.common.service.CommonCache;
import com.oyproj.dao.UserDao;
import com.oyproj.domain.entity.Role;
import com.oyproj.domain.entity.User;
import com.oyproj.domain.entity.UserRole;
import com.oyproj.mapper.RoleMapper;
import com.oyproj.mapper.UserMapper;
import com.oyproj.mapper.UserRoleMapper;
import com.oyproj.service.UserAdminBizService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理后台业务实现
 * 注意：UserBizBase 带 final 字段（userDao/cache），lombok 不生成父类字段的构造参数，
 * 必须像 UserAuthBizServiceImpl 一样手动写构造器。
 */
@Service
public class UserAdminBizServiceImpl extends UserBizBase implements UserAdminBizService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public UserAdminBizServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper,
                                   RoleMapper roleMapper, UserDao userDao, CommonCache cache) {
        super(userDao, cache);
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public Result<PageVo<List<UserAdminItemVo>>> adminPage(UserAdminPageDto dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, User::getStatus, dto.getStatus())
                .and(StringUtils.hasText(dto.getKeyword()), w -> w
                        .like(User::getUsername, dto.getKeyword())
                        .or()
                        .like(User::getEmail, dto.getKeyword()))
                .orderByDesc(User::getCreatedAt);
        Page<User> page = userMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), wrapper);

        // 批量查 ADMIN 关联
        List<String> ids = page.getRecords().stream().map(User::getId).toList();
        Set<String> adminIds = ids.isEmpty() ? Set.of()
                : userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                                .in(UserRole::getUserId, ids))
                        .stream().map(UserRole::getUserId).collect(Collectors.toSet());

        List<UserAdminItemVo> items = page.getRecords().stream().map(u -> {
            UserAdminItemVo vo = copyProperties(u, UserAdminItemVo.class);
            vo.setAdmin(adminIds.contains(u.getId()));
            return vo;
        }).toList();
        PageVo<List<UserAdminItemVo>> resultPage = new PageVo<>(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), (int) page.getPages(), items);
        return Result.ok(resultPage);
    }

    @Override
    public Result<Boolean> ban(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(false);
        }
        user.setStatus(0);
        userMapper.updateById(user);
        clearSession(id);
        return Result.ok(true);
    }

    @Override
    public Result<Boolean> unban(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(false);
        }
        user.setStatus(1);
        userMapper.updateById(user);
        return Result.ok(true);
    }

    @Override
    public Result<Boolean> assignRole(UserRoleAssignDto dto) {
        Role adminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, "ADMIN"));
        if (adminRole == null) {
            return Result.error(false);
        }
        if (Boolean.TRUE.equals(dto.getAdmin())) {
            long exists = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, dto.getUserId())
                    .eq(UserRole::getRoleId, adminRole.getId()));
            if (exists == 0) {
                UserRole userRole = new UserRole();
                userRole.setId(getId());
                userRole.setUserId(dto.getUserId());
                userRole.setRoleId(adminRole.getId());
                userRoleMapper.insert(userRole);
            }
        } else {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, dto.getUserId())
                    .eq(UserRole::getRoleId, adminRole.getId()));
        }
        clearSession(dto.getUserId());
        return Result.ok(true);
    }

    /** 清除用户会话，强制重新登录以刷新角色 */
    private void clearSession(String userId) {
        cache.remove(CachePrefix.USER_ID.getPrefix() + userId);
        cache.remove(CachePrefix.REFRESH_TOKEN.getPrefix() + userId);
    }
}
