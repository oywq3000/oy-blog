package com.oyproj.common.utils;

import com.oyproj.common.base.ResultCode;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 国际化工具类
 */
@Component
public class I18nUtils implements ApplicationContextAware {
//实现ApplicationContextAware 接口的主要作用是让实现类能够获取到 Spring 应用上下文
//当一个类实现了 ApplicationContextAware 接口，
// Spring 容器会在初始化该 bean 时，自动调用其 setApplicationContext(ApplicationContext applicationContext) 方法，将应用上下文注入到该类中。
    private static MessageSource messageSource;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        messageSource  = applicationContext.getBean(MessageSource.class);
    }
    /**
     * 获取国际化消息
     */
    public static String t(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    /**
     * 获取国际化消息
     * @param code 消息码
     * @param args 参数
     * @return 国际化消息
     */
    public static String t(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * 获取国际化消息（如果消息码不存在，则返回默认消息）
     * @param code 消息码
     * @param defaultMessage 默认消息
     * @return 国际化消息
     */
    public static String tOrDefault(String code, String defaultMessage) {
        return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale());
    }

    /**
     * 获取国际化消息（如果消息码不存在，则返回默认消息）
     * @param code 消息码
     * @param defaultMessage 默认消息
     * @param args 参数
     * @return 国际化消息
     */
    public static String tOrDefault(String code, String defaultMessage, Object... args) {
        return messageSource.getMessage(code, args, defaultMessage, LocaleContextHolder.getLocale());
    }

    /**
     * 获取国际化消息（根据指定的Locale）
     * @param code 消息码
     * @param locale 区域设置
     * @return 国际化消息
     */
    public static String tLocale(String code, Locale locale) {
        return messageSource.getMessage(code, null, locale);
    }

    /**
     * 获取国际化消息（根据指定的Locale）
     * @param code 消息码
     * @param locale 区域设置
     * @param args 参数
     * @return 国际化消息
     */
    public static String tLocale(String code, Locale locale, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }

    /**
     * 获取国际化消息（根据ResultCode）
     * @param resultCode 结果码
     * @return 国际化消息
     */
    public static String from(ResultCode resultCode) {
        try {
            return messageSource.getMessage(resultCode.getMessageKey(), null, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return resultCode.getErrMsg();
        }
    }

    /**
     * 获取国际化消息（根据ResultCode）
     * @param resultCode 结果码
     * @param args 参数
     * @return 国际化消息
     */
    public static String from(ResultCode resultCode, Object... args) {
        try {
            return messageSource.getMessage(resultCode.getMessageKey(), args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return resultCode.getErrMsg();
        }
    }

    /**
     * 设置当前线程的Locale
     * @param locale 区域设置
     */
    public static void setLocale(Locale locale) {
        LocaleContextHolder.setLocale(locale);
    }

    /**
     * 从请求的 Accept-Language 列表解析语言（用于网关/Filter 等 LocaleContextHolder 未填充的场景）
     * 优先取第一个 en → 英文(en_US)；zh 或未匹配 → 中文(zh_CN)
     * @param acceptedLocales 请求头的 Accept-Language 解析结果（可为 null/空）
     * @return 用于消息解析的 Locale
     */
    public static Locale resolveLocale(List<Locale> acceptedLocales) {
        if (acceptedLocales == null || acceptedLocales.isEmpty()) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        for (Locale locale : acceptedLocales) {
            String language = locale.getLanguage();
            if ("en".equals(language)) {
                return Locale.US;
            }
            if ("zh".equals(language)) {
                return Locale.SIMPLIFIED_CHINESE;
            }
        }
        return Locale.SIMPLIFIED_CHINESE;
    }

}
