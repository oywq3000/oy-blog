package com.oyproj.dto.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oyproj.common.utils.UUIDUtils;
import com.oyproj.domain.entity.Tag;
import com.oyproj.domain.vo.TagStatVo;
import com.oyproj.dto.TagDao;
import com.oyproj.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @description 标签数据访问实现
 */
@Repository
@RequiredArgsConstructor
public class TagDaoImpl extends ServiceImpl<TagMapper, Tag> implements TagDao {

    /**
     * 按名称查询标签，不存在则自动创建（is_common=0 自创标签）
     *
     * @param name 标签名称
     * @return 标签实体；name 为空时返回 null
     */
    @Override
    public Tag getOrCreateByName(String name) {
        String trimmed = name == null ? null : name.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null; // 空名防御：不触碰数据库
        }
        Tag tag = baseMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, trimmed)
                .last("LIMIT 1")); // name 唯一索引建成前防 selectOne 多行报错
        if (tag != null) {
            return tag;
        }
        // 不存在则自动创建（自创标签）
        Tag created = Tag.builder()
                .id(UUIDUtils.getId())
                .name(trimmed)
                .isCommon(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        try {
            baseMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 并发下另一请求已创建同名标签，重查返回（先例：ArticleInteractionBizServiceImpl.view()）
            Tag existing = baseMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getName, trimmed)
                    .last("LIMIT 1"));
            if (existing != null) {
                return existing;
            }
            throw e;
        }
    }

    /**
     * 常用标签文章数统计（SQL 见 resources/mapper/TagMapper.xml）
     *
     * @return 常用标签统计列表（按文章数降序）
     */
    @Override
    public List<TagStatVo> listCommonTagStats() {
        return baseMapper.listCommonTagStats();
    }
}
