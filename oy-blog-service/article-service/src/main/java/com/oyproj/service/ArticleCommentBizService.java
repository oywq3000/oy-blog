package com.oyproj.service;



import com.oyproj.common.base.Result;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.dto.CommentReactionDto;
import com.oyproj.domain.dto.CommentReplySaveDto;
import com.oyproj.domain.dto.CommentSaveDto;
import com.oyproj.domain.vo.CommentReplyVo;
import com.oyproj.domain.vo.CommentVo;
import com.oyproj.domain.vo.CommentWrapperVo;

import java.util.List;

/**
 * 文章评论业务服务接口
 */
public interface ArticleCommentBizService {

     /**
     * 统计文章评论数量
      *
     * @param articleId 文章ID
     * @return 评论数量
     */
    Result<Long> commentCount(String articleId);

    /**
     * 查询评论列表（分页，默认每页10条）
     * <p>支持请求参数 pageNum / pageSize 控制分页</p>
     *
     * @param articleId 文章ID
     * @param sortBy 排序方式：newest（最新，默认）/ hot（热度）
     * @return 分页评论列表
     */
    Result<PageVo<CommentWrapperVo>> listComments(String articleId, String sortBy);

    /**
     * 查询评论回复列表（分页）
     *
     * @param commentId 评论ID
     * @return 分页回复列表
     */
    Result<PageVo<List<CommentReplyVo>>> listReplies(String commentId);

    /**
     * 添加评论
     *
     * @param dto 评论DTO
     * @return 结果
     */
    Result<Object> addComment(CommentSaveDto dto);

     /**
     * 添加评论回复
     *
     * @param dto 回复DTO
     * @return 结果
     */
    Result<Object> addReply(CommentReplySaveDto dto);

    /**
     * 评论或回复点赞或取消点赞
     *
     * @param dto 点赞或踩DTO
     * @return 结果
     */
    Result<Object> react(CommentReactionDto dto);
}

