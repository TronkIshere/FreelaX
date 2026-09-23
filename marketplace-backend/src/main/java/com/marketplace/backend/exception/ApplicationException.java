package com.marketplace.backend.exception;

import lombok.Getter;

@Getter
public class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] params;

    public ApplicationException(ErrorCode errorCode) {
        this(errorCode, (Object[]) null);
    }

    public ApplicationException(ErrorCode errorCode, Object... params) {
        super(formatMessage(errorCode, params));
        this.errorCode = errorCode;
        this.params = params;
    }

    private static String formatMessage(ErrorCode errorCode, Object[] params) {
        if (params == null || params.length == 0) {
            return errorCode.getMessage();
        }
        return String.format(errorCode.getMessage(), params);
    }
}
