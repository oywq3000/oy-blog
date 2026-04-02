package com.oyproj.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    String value() default "";  // 日志描述信息
    boolean ignoreParams() default false; // 是否忽略参数（如敏感数据）
}