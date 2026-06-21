package com.oyproj.base;
import com.github.pagehelper.PageInfo;
import com.oyproj.common.constant.HeaderConstant;
import com.oyproj.common.service.base.BaseBiz;
import com.oyproj.utils.PageUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.function.Supplier;

/**
 * 基础业务服务类
 */
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
     * 获取分页数据
     *
     * @param supplier 数据提供者（Lambda表达式，执行实际的查询逻辑）
     * @param targetClass 目标VO类
     * @param <T> 源数据类型
     * @param <R> 目标数据类型
     * @return 分页结果
     */
    public <T, R> List<R> getPage(Supplier<List<T>> supplier, Class<R> targetClass) {
        PageUtils.startPage();
        List<T> list = supplier.get();
        PageInfo<T> pageInfo = new PageInfo<>(list);
        PageUtils.clearPage();
        List<R> resultList = copyList(list, targetClass);
        PageInfo<R> resultPageInfo = new PageInfo<>(resultList);
        resultPageInfo.setTotal(pageInfo.getTotal());
        resultPageInfo.setPageNum(pageInfo.getPageNum());
        resultPageInfo.setPageSize(pageInfo.getPageSize());
        resultPageInfo.setPages(pageInfo.getPages());
        return resultList;
    }
}
