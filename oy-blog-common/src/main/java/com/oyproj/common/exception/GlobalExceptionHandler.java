package com.oyproj.common.exception;

import com.oyproj.common.base.BaseException;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import com.oyproj.common.utils.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理基础异常
     */
    @ExceptionHandler(BaseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleBaseException(BaseException e) {
        return Result.error(e.getErrCode(), e.getMessage());
    }

    /**
     * 处理未认证异常
     */
    @ExceptionHandler(UnAuthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result handleUnAuthorizedException(UnAuthorizedException e) {
        return Result.error(e.getErrCode(), e.getMessage());
    }

    /**
     * 处理禁止访问异常
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result handleForbiddenException(ForbiddenException e) {
        return Result.error(e.getErrCode(), e.getMessage());
    }

    /**
     * 处理资源未找到异常
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleNotFoundException(NotFoundException e) {
        return Result.error(e.getErrCode(), e.getMessage());
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleValidationException(ValidationException e) {
        return Result.error(e.getErrCode(), e.getMessage());
    }

    /**
     * 处理其他异常（兜底）
     * 返回统一的 i18n 消息，不把内部异常细节（e.getMessage()）泄露给前端，堆栈打日志
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleException(Exception e) {
        log.error("Unhandled exception", e);
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 处理上传体积超限
     * 之前落到下面的 Exception 兜底 → 500「服务器内部错误」，前端看不出是"文件太大"
     * （线上专栏封面 1.755MB 撞 Spring 默认 1MB 上限时即如此）。
     * 这是客户端传大了，属 400 而非 500；提示里带上服务端实际限制，复用 file.size.exceeded。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        long maxUploadSize = e.getMaxUploadSize();
        log.warn("Upload size exceeded, limit={} bytes", maxUploadSize);
        // 取不到限制值时退回笼统的 400，不要拼出"文件大小超出限制: 0MB"误导用户
        if (maxUploadSize <= 0) {
            return Result.error(ResultCode.BAD_REQUEST);
        }
        return Result.error(ResultCode.BAD_REQUEST.getErrCode(),
                I18nUtils.t("file.size.exceeded", maxUploadSize / (1024 * 1024)));
    }

    /**
     * 处理方法参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 获取第一个验证错误信息（defaultMessage 已是 ValidationMessages 的 i18n 消息）
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse(I18nUtils.from(ResultCode.BAD_REQUEST));
        return Result.error(ResultCode.BAD_REQUEST.getErrCode(), errorMessage);
    }

}