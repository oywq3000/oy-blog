package com.oyproj.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *  Servlet工具类
 */
public class ServletUtils {

    /**
     * 获取当前请求
     * @return 当前请求
     */
    public static HttpServletRequest getRequest(){
        return getRequestAttributes().getRequest();
    }

     /**
     * 获取当前响应
     * @return 当前响应
     */
    public static HttpServletResponse getResponse(){
        return getRequestAttributes().getResponse();
    }

    /**
     * 获取session
     * @return session
     */
    public static HttpSession getSession() {
        return getRequest().getSession();
    }

     /**
     * 获取当前请求参数
     * @return 当前请求参数
     */
    public static Map<String, String[]> getParams(){
        return Collections.unmodifiableMap(getRequest().getParameterMap());
    }

     /**
     * 获取当前请求参数，将参数值转换为字符串
     * @return 当前请求参数
     */
    public static Map<String, String> getParamsMap(){
        return getParams().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue()[0]
                ));
    }

     /**
     * 获取当前请求参数，将参数值转换为字符串数组
     * @return 当前请求参数
     */
    public static Map<String, String[]> getParamsArrayMap(){
        return getParams().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    /**
     * 获取String参数
     * @param name 参数名
     * @return 参数值
     */
    public static String getParameterToString(String name) {
        return ConvertUtils.toStr(getRequest().getParameter(name));
    }
    /**
     * 获取String参数
     * @param name 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public static String getParameterToString(String name, String defaultValue) {
        return ConvertUtils.toStr(getRequest().getParameter(name), defaultValue);
    }

    /**
     * 获取Integer参数
     * @param name 参数名
     * @return 参数值
     */
    public static Integer getParameterToInt(String name) {
        return ConvertUtils.toInt(getRequest().getParameter(name));
    }

    /**
     * 获取Integer参数
     * @param name 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public static Integer getParameterToInt(String name, Integer defaultValue) {
        return ConvertUtils.toInt(getRequest().getParameter(name), defaultValue);
    }

    /**
     * 获取Boolean参数
     * @param name 参数名
     * @return 参数值
     */
    public static Boolean getParameterToBool(String name) {
        return ConvertUtils.toBool(getRequest().getParameter(name));
    }

    /**
     * 获取Boolean参数
     * @param name 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public static Boolean getParameterToBool(String name, Boolean defaultValue) {
        return ConvertUtils.toBool(getRequest().getParameter(name), defaultValue);
    }

    /**
     * 获取当前请求属性
     * @return 当前请求属性
     */
    public static ServletRequestAttributes getRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    }

}
