package com.freelax.avalanchegateway.env;

public class EnvironmentGuardException extends IllegalStateException {

    private final String code;

    public EnvironmentGuardException(String code, String detail) {
        super(code + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.code = code;
    }

    public String code() {
        return code;
    }
}
