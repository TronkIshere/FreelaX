package com.freelax.solanagateway.api;

public final class ApiTypes {

    private ApiTypes() {
    }

    public enum Commitment {
        processed, confirmed, finalized
    }

    public enum ExecutionMode {
        build, send
    }
}
