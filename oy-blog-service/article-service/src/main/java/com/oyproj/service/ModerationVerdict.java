package com.oyproj.service;

/**
 * 审核结论（review_status 三态 + exempt 由调用方直接处理）
 *
 * @param verdict approved=放行 / rejected=驳回 / manual=转人工
 * @param reason  结论理由（给作者和管理员看的）
 */
public record ModerationVerdict(String verdict, String reason) {

    public static ModerationVerdict approved(String reason) {
        return new ModerationVerdict("approved", reason);
    }

    public static ModerationVerdict rejected(String reason) {
        return new ModerationVerdict("rejected", reason);
    }

    public static ModerationVerdict manual(String reason) {
        return new ModerationVerdict("manual", reason);
    }

    public boolean isApproved() {
        return "approved".equals(verdict);
    }

    public boolean isRejected() {
        return "rejected".equals(verdict);
    }

    public boolean isManual() {
        return "manual".equals(verdict);
    }
}
