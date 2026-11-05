package com.freelax.avalanchegateway.applicationException;

public class EnvironmentGuardException extends IllegalStateException {

    private final EnvironmentErrorDefinitions definition;

    public EnvironmentGuardException(EnvironmentErrorDefinitions definition) {
        this(definition, definition.message());
    }

    public EnvironmentGuardException(EnvironmentErrorDefinitions definition, String detail) {
        super(definition.code() + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.definition = definition;
    }

    public String code() {
        return definition.code();
    }
}
