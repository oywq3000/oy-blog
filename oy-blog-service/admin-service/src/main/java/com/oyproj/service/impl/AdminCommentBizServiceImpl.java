package com.oyproj.service.impl;

import com.oyproj.api.article.client.AdminArticleClient;
import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.base.AdminBizBase;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.service.AdminCommentBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCommentBizServiceImpl extends AdminBizBase implements AdminCommentBizService {

    private final AdminArticleClient client;

    @Override
    public Result<PageVo<List<CommentAdminItemVo>>> page(CommentAdminPageDto dto) {
        return client.adminCommentPage(dto);
    }

    @Override
    public Result<Boolean> audit(CommentAuditDto dto) {
        return client.auditComment(dto);
    }

    @Override
    public Result<Boolean> delete(String id) {
        return client.deleteComment(id);
    }

    @Override
    public Result<Boolean> pin(String id, Integer pinned) {
        return client.pinComment(id, pinned);
    }
}
