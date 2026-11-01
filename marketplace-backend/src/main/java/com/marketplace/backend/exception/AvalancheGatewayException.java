package com.marketplace.backend.exception;

import lombok.Getter;

@Getter
public class AvalancheGatewayException extends RuntimeException {

    private final boolean definitive;
    private final Integer httpStatus;
    private final String code;

    public AvalancheGatewayException(String message, boolean definitive, Integer httpStatus, String code) {
        super(message);
        this.definitive = definitive;
        this.httpStatus = httpStatus;
        this.code = code;
    }
}
