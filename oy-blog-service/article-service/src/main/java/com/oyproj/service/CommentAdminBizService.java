package com.oyproj.service;

import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 评论审核后台业务
 */
public interface CommentAdminBizService {

    /** 评论分页列表（按审核状态筛选，默认待审） */
    Result<PageVo<List<CommentAdminItemVo>>> adminPage(CommentAdminPageDto dto);

    /** 审核：1=通过 2=拒绝，写审核日志 */
    Result<Boolean> audit(CommentAuditDto dto);

    /** 删除评论（逻辑删除） */
    Result<Boolean> delete(String id);

    /** 置顶/取消置顶（pinned: 1=置顶 0=取消） */
    Result<Boolean> pin(String id, Integer pinned);
}
