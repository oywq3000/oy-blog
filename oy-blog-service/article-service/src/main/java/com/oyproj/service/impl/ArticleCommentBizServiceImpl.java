package com.oyproj.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oyproj.api.user.client.UserClient;
import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.utils.I18nUtils;
import com.oyproj.common.domain.dto.UserDTO;
import com.oyproj.common.domain.vo.PageVo;
import com.oyproj.domain.dto.CommentReactionDto;
import com.oyproj.domain.dto.CommentReplySaveDto;
import com.oyproj.domain.dto.CommentSaveDto;
import com.oyproj.domain.entity.Comment;
import com.oyproj.domain.entity.CommentReply;
import com.oyproj.domain.vo.CommentReplyVo;
import com.oyproj.domain.vo.CommentVo;
import com.oyproj.domain.vo.CommentWrapperVo;
import com.oyproj.domain.vo.PageDomain;
import com.oyproj.domain.vo.TableSupport;
import com.oyproj.dto.ArticleStatsDao;
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
    @NotNull private final ArticleStatsDao statsDao;

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
     * @param articleId 文章ID
     * @param sortBy 排序方式：newest（最新）/ hot（热度）
     */
    @Override
    @Transactional
    public Result<PageVo<CommentWrapperVo>> listComments(String articleId, String sortBy) {
        if ("hot".equals(sortBy)) {
            return listCommentsByHot(articleId);
        }
        return listCommentsByNewest(articleId);
    }

    /**
     * 按最新排序查询评论（置顶优先 + 时间倒序 + MP 分页）
     */
    private Result<PageVo<CommentWrapperVo>> listCommentsByNewest(String articleId) {
        // ===== 第1次查询：分页查评论（置顶优先 + 时间倒序） =====
        PageDomain pd = TableSupport.getPageDomain();
        Page<Comment> page = new Page<>(pd.getPageNum(), pd.getPageSize());
        List<Comment> commentList = commentDao.listByArticleOrderByNewest(articleId, page);

        // 提取分页元数据
        Integer currentPage = (int) page.getCurrent();
        Integer pageSize = (int) page.getSize();
        Long total = page.getTotal();
        Integer totalPages = (int) page.getPages();

        if (commentList.isEmpty()) {
            PageVo<CommentWrapperVo> emptyPage = new PageVo<>(currentPage, pageSize, total, totalPages, new CommentWrapperVo(0, new ArrayList<>()));
            return Result.ok(emptyPage);
        }

        // 聚合 + 组装 VO
        List<CommentVo> voList = assembleCommentVos(commentList);
        long totalReplyCount = replyDao.countByArticleId(articleId);
        long totalCommentCount = total + totalReplyCount;
        CommentWrapperVo wrapper = new CommentWrapperVo(totalCommentCount, voList);
        PageVo<CommentWrapperVo> resultPage = new PageVo<>(currentPage, pageSize, total, totalPages, wrapper);
        return Result.ok(resultPage);
    }

    /**
     * 按热度排序查询评论（SQL 层排序 + 分页，只返回当前页 20 条）
     * 排序规则：置顶优先 + (likeCount + replyCount*2) DESC
     */
    private Result<PageVo<CommentWrapperVo>> listCommentsByHot(String articleId) {
        int[] pageParm = getPageParamFromRequest();
        int pageNum = pageParm[0];
        int pageSize = pageParm[1];
        int offset = (pageNum - 1) * pageSize;
        // 1. 总数
        long total = commentDao.countByArticle(articleId);
        if (total == 0) {
            PageVo<CommentWrapperVo> emptyPage = new PageVo<>(pageNum, pageSize, 0L, 0,
                    new CommentWrapperVo(0, new ArrayList<>()));
            return Result.ok(emptyPage);
        }
        // 2. SQL 层：热度排序 + 分页，只返回当前页 20 条
        List<Comment> commentList = commentDao.listByArticleOrderByHot(articleId, offset, pageSize);
        if (commentList.isEmpty()) {
            PageVo<CommentWrapperVo> emptyPage = new PageVo<>(pageNum, pageSize, total, 0,
                    new CommentWrapperVo(0, new ArrayList<>()));
            return Result.ok(emptyPage);
        }
        // 3. 只为当前页 20 条聚合 reaction/用户信息/回复数
        List<CommentVo> voList = assembleCommentVos(commentList);
        int totalPages = (int) Math.ceil((double) total / pageSize);
        long totalReplyCount = replyDao.countByArticleId(articleId);
        long totalCommentCount = total + totalReplyCount;
        CommentWrapperVo wrapper = new CommentWrapperVo(totalCommentCount, voList);
        PageVo<CommentWrapperVo> resultPage = new PageVo<>(pageNum, pageSize, total, totalPages, wrapper);
        return Result.ok(resultPage);
    }

    /**
     * 从请求中读取 pageNum，默认 1
     */
    private int[] getPageParamFromRequest() {
        int[] param = new int[]{1,1};
        try {
            jakarta.servlet.http.HttpServletRequest request =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest();
            String pageNumStr = request.getParameter("pageNum");
            String pageSizeStr = request.getParameter("pageSize");
            if (pageNumStr != null && !pageNumStr.isEmpty()) {
                param[0]=Integer.parseInt(pageNumStr);
                param[1] = Integer.parseInt(pageSizeStr);
            }
        } catch (Exception ignored) {

        }
        return param;
    }

    /**
     * 组装 CommentVo 列表：批量拉取用户信息 + reaction 聚合 + 回复数
     */
    private List<CommentVo> assembleCommentVos(List<Comment> commentList) {
        // 获取当前用户ID（未登录时为 null）
        String userId = null;
        try { userId = getUserId(); } catch (Exception ignored) {}

        List<String> commentIds = commentList.stream().map(Comment::getId).collect(Collectors.toList());

        // 批量拉取所有相关用户信息
        List<String> allUserIds = commentList.stream()
                .map(Comment::getUserId)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Map<String, UserDTO> userMap = fetchUserInfoMap(allUserIds);

        // 批量查 reaction 聚合计数 + 当前用户表态 + 回复总数
        Map<String, Map<String, Long>> reactionCounts = reactionDao.getReactionCounts(commentIds, null);
        Map<String, String> userReactions = reactionDao.getUserReactions(commentIds, null, userId);
        Map<String, Long> replyCountMap = replyDao.countByCommentIds(commentIds);

        // 组装 VO
        return commentList.stream().map(comment -> {
            CommentVo vo = copyProperties(comment, CommentVo.class);

            Map<String, Long> counts = reactionCounts.getOrDefault(comment.getId(), Collections.emptyMap());
            vo.setLikeCount(counts.getOrDefault("like", 0L));
            vo.setDislikeCount(counts.getOrDefault("dislike", 0L));
            vo.setUserReaction(userReactions.get(comment.getId()));
            vo.setIsShow(!"dislike".equals(userReactions.get(comment.getId())));

            UserDTO commentUser = userMap.get(comment.getUserId());
            if (commentUser != null) {
                vo.setUsername(commentUser.getUsername());
                vo.setAvatar(commentUser.getAvatarUrl());
            } else {
                vo.setUsername("User-" + comment.getUserId());
            }
            vo.setReplyCount(replyCountMap.getOrDefault(comment.getId(), 0L));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 查询评论回复列表（分页，含表态数据）
     */
    @Override
    public Result<PageVo<List<CommentReplyVo>>> listReplies(String commentId) {
        // ===== 分页查回复 =====
        PageDomain pd = TableSupport.getPageDomain();
        Page<CommentReply> page = new Page<>(pd.getPageNum(), pd.getPageSize());
        List<CommentReply> replies = replyDao.listByCommentId(commentId, page);

        Integer currentPage = (int) page.getCurrent();
        Integer pageSize = (int) page.getSize();
        Long total = page.getTotal();
        Integer totalPages = (int) page.getPages();

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
        comment.setStatus(1);
        comment.setCommentAt(LocalDateTime.now());

        // 计算楼层：当前文章最大楼层 + 1
        Integer maxFloor = commentDao.getMaxFloor(comment.getArticleId());
        comment.setFloor(maxFloor != null ? maxFloor + 1 : 1);

        commentDao.save(comment);
        // 更新文章评论统计
        statsDao.incComments(comment.getArticleId(), 1);
        // 返回新建评论（含用户名/头像等展示信息），前端据此乐观插入列表，避免整表重载
        CommentVo vo = assembleCommentVos(Collections.singletonList(comment)).get(0);
        return Result.ok(vo);
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
        commentReply.setStatus(1);
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
                return Result.error(ResultCode.FAIL.getErrCode(), I18nUtils.t("comment.not_found"));
            }
        }

        replyDao.save(commentReply);
        // 返回新建回复（含用户名/头像等展示信息），前端据此就地追加，避免重拉该页
        List<String> userIds = new ArrayList<>();
        userIds.add(commentReply.getUserId());
        if (commentReply.getReplyToUserId() != null) {
            userIds.add(commentReply.getReplyToUserId());
        }
        CommentReplyVo vo = buildReplyVo(commentReply, Collections.emptyMap(), Collections.emptyMap(), fetchUserInfoMap(userIds));
        return Result.ok(vo);
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
        if(dto.getReplyId()!=null){
            reactionDao.reactToReply(dto.getArticleId(), dto.getReplyId(),userId, dto.getType());
        }else{
            reactionDao.reactToComment(dto.getArticleId(), dto.getCommentId(), userId, dto.getType());
        }
        return Result.ok();
    }
}
