package com.oyproj.service;

/**
 * 文章 AI 审核服务：豁免判定、调用 BlogAgent 审核端点、写审核日志。
 * 铁律：传输层/响应转换异常一律抛出（由消费端重试阶梯接管，绝不静默放行）；
 * AI 返回未知 verdict 时回退 manual（转人工）——AI 挂了不等于审核门洞开。
 */
public interface ModerationService {

    boolean isEnabled();

    /** 当前用户是否豁免（读网关注入的 X-User-Type，如 READER/ADMIN/GUEST） */
    boolean isExempt();

    /** 读请求头 X-User-Type（网关 AuthenticationFilter 注入；管理端经 AdminFeignConfig 透传） */
    String getCurrentUserType();

    /**
     * 调用 BlogAgent 审核。
     * 传输层/响应转换异常（连接失败/超时/4xx/5xx/转换失败）→ 抛出原始异常，
     * 由消费端重试阶梯接管（不降级 manual，否则 Task 3 的 TTL 重试回路永远不可达）。
     * AI 返回未知 verdict → manual（fail-closed）。
     */
    ModerationVerdict moderate(String articleId, String title, String summary, String contentMd);

    /** 写审核日志（operatorId："ai" 表示 AI 判定，人工审核时为管理员 ID） */
    void writeLog(String articleId, String action, String reason, String operatorId);
}
