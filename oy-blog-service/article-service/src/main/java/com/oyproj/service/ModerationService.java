package com.oyproj.service;

import com.oyproj.base.ArticleBaseBizService;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.config.ModerationProperties;
import com.oyproj.domain.entity.ModerationLog;
import com.oyproj.mapper.ModerationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 文章 AI 审核服务：豁免判定、调用 BlogAgent 审核端点、写审核日志。
 * 铁律：传输层/响应转换异常一律抛出（由消费端重试阶梯接管，绝不静默放行）；
 * AI 返回未知 verdict 时回退 manual（转人工）——AI 挂了不等于审核门洞开。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService extends ArticleBaseBizService {

    private final RestClient moderationRestClient;
    private final ModerationProperties properties;
    private final ModerationLogMapper moderationLogMapper;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 当前用户是否豁免（读网关注入的 X-User-Type，如 READER/ADMIN/GUEST） */
    public boolean isExempt() {
        String userType = getCurrentUserType();
        if (!StringUtils.hasText(userType)) {
            return false; // 读不到角色 → 保守方向：不豁免
        }
        return properties.getExemptRoles().stream()
                .anyMatch(role -> role.equalsIgnoreCase(userType));
    }

    /** 读请求头 X-User-Type（网关 AuthenticationFilter 注入；管理端经 AdminFeignConfig 透传） */
    public String getCurrentUserType() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader(HeaderConstant.USER_TYPE.getValue());
    }

    /**
     * 调用 BlogAgent 审核。
     * 传输层/响应转换异常（连接失败/超时/4xx/5xx/转换失败）→ 抛出原始异常，
     * 由消费端重试阶梯接管（不降级 manual，否则 Task 3 的 TTL 重试回路永远不可达）。
     * AI 返回未知 verdict → manual（fail-closed）。
     */
    public ModerationVerdict moderate(String articleId, String title, String summary, String contentMd) {
        Map<String, String> body = new HashMap<>();
        body.put("articleId", articleId);
        body.put("title", title);
        body.put("summary", summary == null ? "" : summary);
        body.put("content", contentMd);
        try {
            Map<?, ?> resp = moderationRestClient.post()
                    .uri("/moderate/article")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String verdict = resp == null ? null : String.valueOf(resp.get("verdict"));
            String reason = resp == null ? "" : String.valueOf(resp.get("reason"));
            if ("reject".equals(verdict)) {
                return ModerationVerdict.rejected(reason);
            }
            if ("approve".equals(verdict)) {
                return ModerationVerdict.approved(reason);
            }
            return ModerationVerdict.manual(reason); // manual 或未知值 → 转人工
        } catch (Exception e) {
            log.warn("文章 AI 审核调用失败, articleId: {}, 错误: {}", articleId, e.getMessage());
            throw e;
        }
    }

    /** 写审核日志（operatorId："ai" 表示 AI 判定，人工审核时为管理员 ID） */
    public void writeLog(String articleId, String action, String reason, String operatorId) {
        ModerationLog logEntity = new ModerationLog();
        logEntity.setId(getId());
        logEntity.setArticleId(articleId);
        logEntity.setAction(action);
        logEntity.setReason(reason);
        logEntity.setOperatorId(operatorId);
        logEntity.setActedAt(LocalDateTime.now());
        moderationLogMapper.insert(logEntity);
    }
}
