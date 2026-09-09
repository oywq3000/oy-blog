package com.oyproj.base;
import com.oyproj.common.constant.BlogRole;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.service.base.BaseBiz;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基础业务服务类
 */
@Slf4j
public class ArticleBaseBizService extends BaseBiz {
    protected String getUserId(){
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader(HeaderConstant.USER_ID.getValue());
    }

    /**
     * 当前请求的用户类型（读网关注入的 X-User-Type：READER/ADMIN/GUEST；无请求上下文返回 null）。
     * 判定口径与 ModerationServiceImpl / RequirePermissionInterceptor 一致。
     */
    public String getCurrentUserType() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getHeader(HeaderConstant.USER_TYPE.getValue());
    }

    /**
     * 当前请求是否管理员（X-User-Type == ADMIN）。
     * 网关保证该头每次转发都会用真实角色覆盖，不可伪造（见 RequirePermissionInterceptor 注释）。
     */
    protected boolean isAdminUser() {
        return BlogRole.ADMIN.name().equals(getCurrentUserType());
    }
}
