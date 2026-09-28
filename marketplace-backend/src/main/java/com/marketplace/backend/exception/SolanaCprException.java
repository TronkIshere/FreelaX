package com.marketplace.backend.exception;

import lombok.Getter;

@Getter
public class SolanaCprException extends RuntimeException {

    private final boolean definitive;
    private final Integer httpStatus;

    public SolanaCprException(String message, boolean definitive, Integer httpStatus) {
        super(message);
        this.definitive = definitive;
        this.httpStatus = httpStatus;
    }
}