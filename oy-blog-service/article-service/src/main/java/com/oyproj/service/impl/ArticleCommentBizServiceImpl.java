package com.oyproj.service.impl;

import com.oyproj.api.user.client.UserClient;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.dto.CommentReactionDto;
import com.oyproj.domain.dto.CommentReplySaveDto;
import com.oyproj.domain.dto.CommentSaveDto;
import com.oyproj.domain.entity.Comment;
import com.oyproj.domain.entity.CommentReply;
import com.oyproj.domain.vo.CommentReplyVo;
import com.oyproj.domain.vo.CommentVo;
import com.oyproj.dto.CommentDao;
import com.oyproj.dto.CommentReactionDao;
import com.oyproj.dto.CommentReplyDao;
import com.oyproj.service.ArticleCommentBizService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 文章评论业务服务实现类
 *
 * 数据查询策略：批量查询方案
 * 1. 查评论（分页）
 * 2. 批量查回复
 * 3. 批量调用 UserClient 拉取用户信息
 * 4. 批量查 reaction 聚合计数 + 当前用户表态
 * 避免 N+1 查询，避免复杂 JOIN 破坏分页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleCommentBizServiceImpl extends ArticleBaseBizService implements ArticleCommentBizService {

    @NotNull private final CommentDao commentDao;
    @NotNull private final CommentReplyDao replyDao;
    @NotNull private final CommentReactionDao reactionDao;
    @NotNull private final UserClient userClient;

    /**
     * 统计文章评论数量
     */
    @Override
    public Result<Long> commentCount(String articleId) {
        long count = commentDao.countByArticle(articleId);
        return Result.ok(count);
    }

    /**
     * 查询评论列表（分页，含前3条回复预览 + 表态数据）
     *
     * 两次查询方案：
     * 1. 分页查评论
     * 2. 批量查回复 + reaction 聚合 + 当前用户表态
     */
    @Override
    public Result<PageVo<List<CommentVo>>> listComments(String articleId) {
        // ===== 第1次查询：分页查评论 =====
        PageVo<List<Comment>> entityPage = getPageVo(() -> commentDao.listByArticle(articleId), Comment.class);
        List<Comment> commentList = entityPage.getData();

        // 提取分页元数据
        Integer currentPage = entityPage.getCurrentPage();
        Integer pageSize = entityPage.getPageSize();
        Long total = entityPage.getTotal();
        Integer totalPages = entityPage.getTotalPages();

        if (commentList.isEmpty()) {
            PageVo<List<CommentVo>> emptyPage = new PageVo<>(currentPage, pageSize, total, totalPages, new ArrayList<>());
            return Result.ok(emptyPage);
        }

        // 获取当前用户ID（未登录时为 null）
        String userId = null;
        try { userId = getUserId(); } catch (Exception ignored) {}

        List<String> commentIds = commentList.stream().map(Comment::getId).collect(Collectors.toList());

        // ===== 第2次查询：每条评论批量取前3条回复 =====
        List<CommentReply> allReplies = replyDao.listRepliesByCommentIds(commentIds, 10);
        List<String> replyIds = allReplies.stream().map(CommentReply::getId).collect(Collectors.toList());

        // 按 commentId 分组
        Map<String, List<CommentReply>> repliesByComment = allReplies.stream()
                .collect(Collectors.groupingBy(CommentReply::getCommentId));

        // ===== 第3次查询：批量拉取所有相关用户信息 =====
        List<String> allUserIds = commentList.stream()
                .map(Comment::getUserId)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        allReplies.forEach(r -> {
            allUserIds.add(r.getUserId());
            if (r.getReplyToUserId() != null) {
                allUserIds.add(r.getReplyToUserId());
            }
        });
        Map<String, UserDTO> userMap = fetchUserInfoMap(allUserIds);

        // ===== 第4次查询：批量查 reaction 聚合计数 + 当前用户表态 + 回复总数 =====
        Map<String, Map<String, Long>> reactionCounts = reactionDao.getReactionCounts(commentIds, replyIds);
        Map<String, String> userReactions = reactionDao.getUserReactions(commentIds, replyIds, userId);
        Map<String, Long> replyCountMap = replyDao.countByCommentIds(commentIds);

        // ===== 组装 VO =====
        List<CommentVo> voList = commentList.stream().map(comment -> {
            CommentVo vo = copyProperties(comment, CommentVo.class);

            // -- 表态统计 --
            Map<String, Long> counts = reactionCounts.getOrDefault(comment.getId(), Collections.emptyMap());
            vo.setLikeCount(counts.getOrDefault("like", 0L));
            vo.setDislikeCount(counts.getOrDefault("dislike", 0L));
            vo.setUserReaction(userReactions.get(comment.getId()));

            // -- isShow: 当前用户踩过此评论则隐藏 --
            vo.setIsShow(!"dislike".equals(userReactions.get(comment.getId())));

            // -- 用户信息 --
            UserDTO commentUser = userMap.get(comment.getUserId());
            if (commentUser != null) {
                vo.setUsername(commentUser.getUsername());
                vo.setAvatar(commentUser.getAvatarUrl());
            } else {
                vo.setUsername("User-" + comment.getUserId());
            }

            // -- 回复数量（真实总数） --
            List<CommentReply> commentReplies = repliesByComment.getOrDefault(comment.getId(), Collections.emptyList());
            vo.setReplyCount(replyCountMap.getOrDefault(comment.getId(), 0L));

            // -- 前3条回复预览 --
            List<CommentReplyVo> previewReplies = commentReplies.stream()
                    .limit(3)
                    .map(r -> buildReplyVo(r, reactionCounts, userReactions, userMap))
                    .collect(Collectors.toList());
            vo.setReplies(previewReplies);

            return vo;
        }).collect(Collectors.toList());

        PageVo<List<CommentVo>> resultPage = new PageVo<>(currentPage, pageSize, total, totalPages, voList);
        return Result.ok(resultPage);
    }

    /**
     * 查询评论回复列表（分页，含表态数据）
     */
    @Override
    public Result<PageVo<List<CommentReplyVo>>> listReplies(String commentId) {
        // ===== 分页查回复 =====
        PageVo<List<CommentReply>> entityPage = getPageVo(() -> replyDao.listByCommentId(commentId), CommentReply.class);
        List<CommentReply> replies = entityPage.getData();

        Integer currentPage = entityPage.getCurrentPage();
        Integer pageSize = entityPage.getPageSize();
        Long total = entityPage.getTotal();
        Integer totalPages = entityPage.getTotalPages();

        if (replies.isEmpty()) {
            PageVo<List<CommentReplyVo>> emptyPage = new PageVo<>(currentPage, pageSize, total, totalPages, new ArrayList<>());
            return Result.ok(emptyPage);
        }

        // 获取当前用户ID
        String userId = null;
        try { userId = getUserId(); } catch (Exception ignored) {}

        List<String> replyIds = replies.stream().map(CommentReply::getId).collect(Collectors.toList());

        // 批量拉取所有相关用户信息
        List<String> allUserIds = new ArrayList<>();
        replies.forEach(r -> {
            allUserIds.add(r.getUserId());
            if (r.getReplyToUserId() != null) {
                allUserIds.add(r.getReplyToUserId());
            }
        });
        Map<String, UserDTO> userMap = fetchUserInfoMap(allUserIds);

        // 批量查 reaction 聚合 + 当前用户表态
        Map<String, Map<String, Long>> reactionCounts = reactionDao.getReactionCounts(null, replyIds);
        Map<String, String> userReactions = reactionDao.getUserReactions(null, replyIds, userId);

        List<CommentReplyVo> voList = replies.stream()
                .map(r -> buildReplyVo(r, reactionCounts, userReactions, userMap))
                .collect(Collectors.toList());

        PageVo<List<CommentReplyVo>> resultPage = new PageVo<>(currentPage, pageSize, total, totalPages, voList);
        return Result.ok(resultPage);
    }

    /**
     * 将 CommentReply 实体转为 CommentReplyVo（含表态数据）
     */
    private CommentReplyVo buildReplyVo(CommentReply r,
                                         Map<String, Map<String, Long>> reactionCounts,
                                         Map<String, String> userReactions,
                                         Map<String, UserDTO> userMap) {
        CommentReplyVo vo = copyProperties(r, CommentReplyVo.class);

        // 表态统计
        Map<String, Long> counts = reactionCounts.getOrDefault(r.getId(), Collections.emptyMap());
        vo.setLikeCount(counts.getOrDefault("like", 0L));
        vo.setDislikeCount(counts.getOrDefault("dislike", 0L));
        vo.setUserReaction(userReactions.get(r.getId()));

        // isShow
        vo.setIsShow(!"dislike".equals(userReactions.get(r.getId())));

        // 用户信息
        UserDTO replyUser = userMap.get(r.getUserId());
        if (replyUser != null) {
            vo.setUsername(replyUser.getUsername());
            vo.setAvatar(replyUser.getAvatarUrl());
        } else {
            vo.setUsername("User-" + r.getUserId());
        }
        if (r.getReplyToUserId() != null) {
            UserDTO replyToUser = userMap.get(r.getReplyToUserId());
            if (replyToUser != null) {
                vo.setReplyToUsername(replyToUser.getUsername());
            } else {
                vo.setReplyToUsername("User-" + r.getReplyToUserId());
            }
        }

        return vo;
    }

    /**
     * 批量拉取用户信息
     *
     * @param userIds 用户ID列表
     * @return userId → UserDTO 映射
     */
    private Map<String, UserDTO> fetchUserInfoMap(List<String> userIds) {
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, UserDTO> userMap = new HashMap<>();
        try {
            Result<List<UserDTO>> result = userClient.getUserDTOs(userIds.stream().distinct().collect(Collectors.toList()));
            if (result != null && result.getIsSuccess() && result.getData() != null) {
                result.getData().forEach(dto -> userMap.put(dto.getId(), dto));
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败, userIds: {}", userIds, e);
        }
        return userMap;
    }

    /**
     * 发表评论
     */
    @Override
    public Result<Object> addComment(CommentSaveDto dto) {
        Comment comment = copyProperties(dto, Comment.class);
        comment.setId(getId());
        comment.setUserId(getUserId());
        comment.setCommentAt(LocalDateTime.now());

        // 计算楼层：当前文章最大楼层 + 1
        Integer maxFloor = commentDao.getMaxFloor(comment.getArticleId());
        comment.setFloor(maxFloor != null ? maxFloor + 1 : 1);

        commentDao.save(comment);
        return Result.ok();
    }

    /**
     * 回复评论或回复者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Object> addReply(CommentReplySaveDto dto) {

        CommentReply commentReply = copyProperties(dto, CommentReply.class);
        commentReply.setId(getId());
        commentReply.setUserId(getUserId());
        commentReply.setReplyAt(LocalDateTime.now());

        // 确保 articleId 存在。如果前端没传，需要先查 Comment 补全
        if (commentReply.getArticleId() == null && commentReply.getCommentId() != null) {
            Comment comment = commentDao.getById(commentReply.getCommentId());
            if (comment != null) {
                commentReply.setArticleId(comment.getArticleId());

                // 更新主评论的 has_reply 状态
                if (comment.getHasReply() == null || comment.getHasReply() == 0) {
                    comment.setHasReply(1);
                    commentDao.updateById(comment);
                }
            } else {
                return Result.error("评论不存在");
            }
        }

        replyDao.save(commentReply);
        return Result.ok();
    }

    /**
     * 评论/回复表态（点赞/踩）
     *
     * Toggle 逻辑（在 DAO 层实现）：
     * - 无表态 → INSERT
     * - 同类型 → DELETE（取消）
     * - 不同类型 → UPDATE（切换）
     */
    @Override
    public Result<Object> react(CommentReactionDto dto) {
        String userId = getUserId();
        if (dto.getCommentId() != null) {
            reactionDao.reactToComment(dto.getArticleId(), dto.getCommentId(), userId, dto.getType());
        } else if (dto.getReplyId() != null) {
            reactionDao.reactToReply(dto.getArticleId(), dto.getReplyId(), userId, dto.getType());
        }
        return Result.ok();
    }
}
