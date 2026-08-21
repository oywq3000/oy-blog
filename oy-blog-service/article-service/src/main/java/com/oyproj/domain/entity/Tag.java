package com.oyproj.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标签实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tag")
public class Tag {

    /**
     * 标签ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 名称（标签唯一标识）
     */
    @TableField("name")
    private String name;

    /**
     * 是否常用标签：1=常用(管理员预置) 0=自创(保存文章时自动创建)
     */
    @TableField("is_common")
    private Integer isCommon;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

