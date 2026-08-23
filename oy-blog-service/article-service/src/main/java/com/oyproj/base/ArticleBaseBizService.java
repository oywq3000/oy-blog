package com.oyproj.base;
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
}
