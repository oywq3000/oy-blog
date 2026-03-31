package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.domain.entity.Category;
import com.oyproj.dto.CategoryDao;
import com.oyproj.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * @author LX
 * @date 2025/12/03
 * @description 分类数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class CategoryDaoImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryDao {

    /**
     * 根据编码查询分类
     *
     * @param code 分类唯一编码
     * @return 分类实体
     */
    @Override
    public Category getByCode(String code) {
        return baseMapper.selectOne(new LambdaQueryWrapper<Category>().eq(Category::getCode, code));
    }
}

