package com.freelax.avalanchegateway.applicationException;

/** An error with a stable code and an HTTP status. Messages never carry key material, URLs or stack details. */
public class ApiException extends RuntimeException {

    private final AvalancheErrorDefinitions definition;

    public ApiException(AvalancheErrorDefinitions definition) {
        this(definition, definition.message());
    }

    public ApiException(AvalancheErrorDefinitions definition, String detail) {
        super(detail);
        this.definition = definition;
    }

    public int status() {
        return definition.status();
    }

    public String code() {
        return definition.code();
    }

    public AvalancheErrorDefinitions definition() {
        return definition;
    }
}
