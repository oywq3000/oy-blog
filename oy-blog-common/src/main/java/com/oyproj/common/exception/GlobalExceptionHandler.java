package com.oyproj.common.exception;

import com.oyproj.common.base.BaseException;
import com.oyproj.common.base.Result;
import com.oyproj.common.base.ResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
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
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleException(Exception e) {
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR.getErrCode(), e.getMessage());
    }
}