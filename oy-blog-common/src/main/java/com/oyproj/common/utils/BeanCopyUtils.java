package com.oyproj.common.utils;

import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  Bean工具类，封装Spring BeanUtils
 */
public class BeanCopyUtils {

    /**
     * 属性拷贝
     *
     * @param source 源对象
     * @param target 目标对象
     */
    public static void copyProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        BeanUtils.copyProperties(source, target);
    }

    /**
     * 属性拷贝，返回新对象
     *
     * @param source      源对象
     * @param targetClass 目标对象类
     * @param <T>         目标对象类型
     * @return 目标对象实例
     */
    public static <T> T copyProperties(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Bean copy error: " + e.getMessage(), e);
        }
    }

    /**
     * 列表拷贝
     *
     * @param sourceList  源列表
     * @param targetClass 目标对象类
     * @param <T>         目标对象类型
     * @return 目标列表
     */
    public static <T> List<T> copyList(List<?> sourceList, Class<T> targetClass) {
        if (CollectionUtils.isEmpty(sourceList)) {
            return Collections.emptyList();
        }
        return sourceList.stream()
                .map(source -> copyProperties(source, targetClass))
                .collect(Collectors.toList());
    }
}
