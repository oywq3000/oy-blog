package com.oyproj.service;

import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;

import java.util.List;

/**
 * 评论审核 BFF 业务：透传 Feign 调用 article-service
 */
public interface AdminCommentBizService {

    Result<PageVo<List<CommentAdminItemVo>>> page(CommentAdminPageDto dto);

    Result<Boolean> audit(CommentAuditDto dto);

    Result<Boolean> delete(String id);

    Result<Boolean> pin(String id, Integer pinned);
}
