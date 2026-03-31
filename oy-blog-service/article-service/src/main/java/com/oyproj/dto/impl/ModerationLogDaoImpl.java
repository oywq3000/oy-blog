package com.oyproj.dto.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.oyproj.domain.entity.ModerationLog;
import com.oyproj.dto.ModerationLogDao;
import com.oyproj.mapper.ModerationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审核日志数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class ModerationLogDaoImpl extends ServiceImpl<ModerationLogMapper, ModerationLog> implements ModerationLogDao {

    /**
     * 根据文章ID查询审核日志列表
     *
     * @param articleId 文章ID
     * @return 审核日志列表
     */
    @Override
    public List<ModerationLog> listByArticle(String articleId) {
        return baseMapper.selectList(new LambdaQueryWrapper<ModerationLog>()
                .eq(ModerationLog::getArticleId, articleId)
                .orderByDesc(ModerationLog::getActedAt));
    }
}

