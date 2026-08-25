package com.oyproj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.article.domain.dto.CommentAdminPageDto;
import com.oyproj.api.article.domain.dto.CommentAuditDto;
import com.oyproj.api.article.domain.vo.CommentAdminItemVo;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.entity.Comment;
import com.oyproj.domain.entity.ModerationLog;
import com.oyproj.mapper.CommentMapper;
import com.oyproj.mapper.ModerationLogMapper;
import com.oyproj.service.CommentAdminBizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论审核后台业务实现
 */
@Service
@RequiredArgsConstructor
public class CommentAdminBizServiceImpl extends ArticleBaseBizService implements CommentAdminBizService {

    private final CommentMapper commentMapper;
    private final ModerationLogMapper moderationLogMapper;

    @Override
    public Result<PageVo<List<CommentAdminItemVo>>> adminPage(CommentAdminPageDto dto) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getStatus() != null, Comment::getStatus, dto.getStatus())
                .orderByAsc(Comment::getStatus)
                .orderByDesc(Comment::getCommentAt);
        Page<Comment> page = commentMapper.selectPage(new Page<>(dto.getPage(), dto.getSize()), wrapper);
        List<CommentAdminItemVo> items = copyList(page.getRecords(), CommentAdminItemVo.class);
        PageVo<List<CommentAdminItemVo>> resultPage = new PageVo<>(
                (int) page.getCurrent(), (int) page.getSize(), page.getTotal(), (int) page.getPages(), items);
        return Result.ok(resultPage);
    }

    @Override
    public Result<Boolean> audit(CommentAuditDto dto) {
        Comment comment = commentMapper.selectById(dto.getCommentId());
        if (comment == null) {
            return Result.error(false);
        }
        comment.setStatus(dto.getStatus());
        commentMapper.updateById(comment);

        ModerationLog log = new ModerationLog();
        log.setId(getId());
        log.setArticleId(comment.getArticleId());
        log.setAction(dto.getStatus() == 1 ? "approve" : "reject");
        log.setReason(dto.getReason());
        log.setOperatorId(getUserId());
        log.setActedAt(LocalDateTime.now());
        moderationLogMapper.insert(log);
        return Result.ok(true);
    }

    @Override
    public Result<Boolean> delete(String id) {
        boolean ok = commentMapper.deleteById(id) > 0;
        return ok ? Result.ok(true) : Result.error(false);
    }

    @Override
    public Result<Boolean> pin(String id, Integer pinned) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            return Result.error(false);
        }
        comment.setIsPinned(pinned);
        commentMapper.updateById(comment);
        return Result.ok(true);
    }
}
