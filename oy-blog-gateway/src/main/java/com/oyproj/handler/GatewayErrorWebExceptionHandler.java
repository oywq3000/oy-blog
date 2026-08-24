package com.oyproj.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyproj.common.base.BaseException;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.exception.ForbiddenException;
import com.oyproj.common.exception.NotFoundException;
import com.oyproj.common.exception.UnAuthorizedException;
import com.oyproj.common.utils.I18nUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.util.Locale;

/**
 * 网关全局错误处理器（WebFlux 版 GlobalExceptionHandler）
 *
 * 把网关链路内抛出的异常统一转成与业务服务一致的 Result JSON：
 *   { "errCode": xxx, "errMsg": "按 Accept-Language 解析的 i18n 文案", "isSuccess": false, "data": null }
 *
 * 说明：
 * 1. Boot 默认的 DefaultErrorWebExceptionHandler 是 @ConditionalOnMissingBean(ErrorWebExceptionHandler.class)，
 *    本类 @Component 注册后自动顶替它，Spring 默认的 {timestamp,status,error,path} 格式不再出现
 * 2. 必须用入参 ex —— ERROR_EXCEPTION_ATTR 是默认 handler 自己放的，已被替换后不存在
 * 3. 网关（WebFlux）不填充 LocaleContextHolder，消息一律用 tLocale + resolveLocale 显式解析
 */
@Slf4j
@Component
@Order(-1)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 响应已提交时无法改写，直接继续抛错
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        Locale locale = I18nUtils.resolveLocale(exchange.getRequest().getHeaders().getAcceptLanguageAsLocales());

        HttpStatus status;
        Integer errCode;
        String message;

        if (ex instanceof UnAuthorizedException e) {
            status = HttpStatus.UNAUTHORIZED;
            errCode = e.getErrCode();
            message = e.getMessage();
        } else if (ex instanceof ForbiddenException e) {
            status = HttpStatus.FORBIDDEN;
            errCode = e.getErrCode();
            message = e.getMessage();
        } else if (ex instanceof NotFoundException e) {
            status = HttpStatus.NOT_FOUND;
            errCode = e.getErrCode();
            message = e.getMessage();
        } else if (ex instanceof BaseException e) {
            status = HttpStatus.BAD_REQUEST;
            errCode = e.getErrCode();
            message = e.getMessage();
        } else if (ex instanceof ResponseStatusException e) {
            // 覆盖未匹配路由等场景：DispatcherHandler 会抛 ResponseStatusException(NOT_FOUND)
            status = HttpStatus.valueOf(e.getStatusCode().value());
            errCode = status.value();
            message = switch (status.value()) {
                case 404 -> I18nUtils.tLocale("error.not_found", locale);
                default -> status.is4xxClientError()
                        ? I18nUtils.tLocale("error.bad_request", locale)
                        : I18nUtils.tLocale("error.internal", locale);
            };
        } else if (ex instanceof ConnectException || ex instanceof WebClientRequestException) {
            // 下游服务不可达
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errCode = ResultCode.SERVICE_UNAVAILABLE.getErrCode();
            message = I18nUtils.tLocale("error.unavailable", locale);
        } else {
            log.error("Gateway unhandled exception", ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            errCode = ResultCode.INTERNAL_SERVER_ERROR.getErrCode();
            message = I18nUtils.tLocale("error.internal", locale);
        }

        // BaseException 部分构造器不传 message（如 errCode+cause），兜底不返回 null
        if (message == null) {
            message = I18nUtils.tLocale("error.internal", locale);
        }

        try {
            byte[] body = objectMapper.writeValueAsBytes(Result.error(errCode, message));
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize gateway error response", e);
            return Mono.error(ex);
        }
    }
}
