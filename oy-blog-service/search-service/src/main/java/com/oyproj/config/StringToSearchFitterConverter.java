package com.oyproj.config;

import com.oyproj.domain.common.SearchFitter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Spring MVC 参数绑定：将前端传来的 "all"/"tag"/"author"/"article" 字符串转为 SearchFitter 枚举
 */
@Component
public class StringToSearchFitterConverter implements Converter<String, SearchFitter> {

    @Override
    public SearchFitter convert(String source) {
        return SearchFitter.fromValue(source);
    }
}
