package com.oyproj.utils;

import com.github.pagehelper.PageHelper;
import com.oyproj.common.utils.SqlUtils;
import com.oyproj.domain.vo.PageDomain;
import com.oyproj.domain.vo.TableSupport;

/**
 * 分页工具
 */
public class PageUtils extends PageHelper{
    /**
     * 开始分页
     */
    public static void startPage() {
        PageDomain pageDomain = TableSupport.getPageDomain();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();
        String orderBy = SqlUtils.escapeOrderBySql(pageDomain.getOrderBy());
        Boolean reasonable = pageDomain.getReasonable();
        PageHelper.startPage(pageNum, pageSize, orderBy).setReasonable(reasonable);
    }
}
