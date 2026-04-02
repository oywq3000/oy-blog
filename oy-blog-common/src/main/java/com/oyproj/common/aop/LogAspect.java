package com.oyproj.common.aop;

import com.oyproj.common.annotation.Log;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class LogAspect {

    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    // 切点：标注了 @Log 注解的方法
    @Pointcut("@annotation(com.oyproj.common.annotation.Log)")
    public void logPointCut() {
    }

    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);
        String description = logAnnotation.value();
        boolean ignoreParams = logAnnotation.ignoreParams();

        // 获取请求信息（如果是Web环境）
        HttpServletRequest request = null;
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                request = attributes.getRequest();
            }
        } catch (Exception e) {
            // 非Web环境忽略
        }

        // 记录入参
        Object[] args = joinPoint.getArgs();
        String params = ignoreParams ? "【参数忽略】" : Arrays.toString(args);

        logger.info("===== AOP Log Start =====");
        logger.info("描述: {}", description);
        if (request != null) {
            logger.info("URL: {}", request.getRequestURL());
            logger.info("HTTP Method: {}", request.getMethod());
            logger.info("IP: {}", request.getRemoteAddr());
        }
        logger.info("类名: {}", signature.getDeclaringTypeName());
        logger.info("方法名: {}", method.getName());
        logger.info("参数: {}", params);

        Object result = null;
        try {
            result = joinPoint.proceed(); // 执行目标方法
            long cost = System.currentTimeMillis() - startTime;
            logger.info("返回值: {}", result);
            logger.info("耗时: {} ms", cost);
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            logger.error("异常: {}", e.getMessage(), e);
            logger.error("耗时: {} ms", cost);
            throw e;
        } finally {
            logger.info("===== AOP Log End =====\n");
        }
    }
}