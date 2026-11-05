package com.freelax.avalanchegateway.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelax.avalanchegateway.applicationException.EnvironmentErrorDefinitions;
import com.freelax.avalanchegateway.applicationException.EnvironmentGuardException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/** What a deployment produced (written by Deploy.s.sol). Carries addresses only, never key material. */
public record DeploymentManifest(int schemaVersion, String environment, long chainId, String escrow, String token, String tokenKind,
                                 String admin, String arbiter, String authSigner, String contractVersion, long startBlock) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");

    public static DeploymentManifest read(Path path) {
        try {
            return parse(Files.readString(path));
        } catch (IOException e) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.MANIFEST_UNREADABLE);
        }
    }

    public static DeploymentManifest parse(String json) {
        JsonNode n;
        try {
            n = JSON.readTree(json);
        } catch (IOException e) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.MANIFEST_INVALID);
        }
        if (n == null || !n.isObject() || n.path("schemaVersion").asInt(-1) != 1) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.MANIFEST_INVALID, "unsupported or missing schemaVersion");
        }
        for (String field : new String[]{"escrow", "token", "admin", "arbiter", "authSigner"}) {
            if (!ADDRESS.matcher(n.path(field).asText("")).matches()) {
                throw new EnvironmentGuardException(EnvironmentErrorDefinitions.MANIFEST_INVALID, field + " must be a 20-byte address");
            }
        }
        if (!n.path("environment").isTextual() || !n.path("chainId").canConvertToLong() || !n.path("tokenKind").isTextual()) {
            throw new EnvironmentGuardException(EnvironmentErrorDefinitions.MANIFEST_INVALID, "environment, chainId and tokenKind are required");
        }
        return new DeploymentManifest(1, n.path("environment").asText(), n.path("chainId").asLong(), lower(n, "escrow"), lower(n, "token"),
                n.path("tokenKind").asText(), lower(n, "admin"), lower(n, "arbiter"), lower(n, "authSigner"),
                n.path("contractVersion").asText(""), n.path("startBlock").asLong(0));
    }

    private static String lower(JsonNode n, String field) {
        return n.path(field).asText().toLowerCase(Locale.ROOT);
    }
}
