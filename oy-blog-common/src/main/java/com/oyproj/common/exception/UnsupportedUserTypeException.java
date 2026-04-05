package com.oyproj.common.exception;

import com.oyproj.common.base.BaseException;
import com.oyproj.common.base.ResultCode;

public class UnsupportedUserTypeException extends BaseException {
    /**
     * 构造函数
     */
    public UnsupportedUserTypeException() {
        super(ResultCode.UNSUPPORTED_USERTYPE);
    }

    /**
     * 构造函数
     * @param message 异常信息
     */
    public UnsupportedUserTypeException(String message) {
        super(ResultCode.UNSUPPORTED_USERTYPE, message);
    }

    /**
     * 构造函数
     * @param resultCode 错误码
     */
    public UnsupportedUserTypeException(ResultCode resultCode) {
        super(resultCode);
    }

    /**
     * 构造函数
     * @param resultCode 错误码
     * @param message 异常信息
     */
    public UnsupportedUserTypeException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }

    /**
     * 构造函数
     * @param message 异常信息
     * @param cause 异常原因
     */
    public UnsupportedUserTypeException(String message, Throwable cause) {
        super(ResultCode.UNSUPPORTED_USERTYPE, message, cause);
    }
}
