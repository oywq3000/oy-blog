package com.oyproj.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 分页插件配置
 *
 * <p>项目统一使用 MP 分页（selectPage），不再使用 PageHelper。
 * overflow=true 的行为是：<b>页码越界（current &gt; pages）时回到第一页</b>
 * （见 {@code PaginationInnerInterceptor.handlerOverflow()} 的 {@code page.setCurrent(1)}）——
 * <b>不是</b>"回退到最后一页"，别按后者推理。</p>
 *
 * <p><b>页号是 1-based</b>：{@code IPage.offset()} 对 {@code current <= 1} 一律返回 0，
 * 所以 {@code new Page<>(0, size)} 与 {@code new Page<>(1, size)} 是<b>同一页</b>。
 * 调用方（如快照/对账的 {@code pageNum}）必须从 <b>1</b> 开始翻：从 0 开始会<b>重复第一页</b>、
 * 此后每次请求相对页码整体错位一页；若循环以总页数为上界（如 {@code pageNum < totalPages}），
 * 会更早 break 而<b>漏掉末尾那一页</b>（对账器当初正是这样把末尾文章漏出权威集合、当成僵尸误删）。
 * 注意它<b>并非</b>"再也翻不到后面的页"——那个过度概括的说法曾长期存活在本类注释里（就是这一处），
 * 已按 {@code ArticleIndexClient} / {@code IndexReconciler} 的同类说明更正。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setOverflow(true);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
