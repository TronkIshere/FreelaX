package com.freelax.avalanchegateway.api;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.startup.EnvironmentStartupValidator;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/avalanche")
public class StatusController {

    private final ChainClient chainClient;
    private final EnvironmentStartupValidator validator;

    public StatusController(ChainClient chainClient, EnvironmentStartupValidator validator) {
        this.chainClient = chainClient;
        this.validator = validator;
    }

    /** Performs live read-only RPC calls through the Spring-managed web3j client on every request. */
    @GetMapping("/status")
    public StatusResponse status() {
        ValidatedEnvironment validated = validator.validated();
        long chainId = chainClient.chainId();
        String clientVersion = chainClient.clientVersion();
        return new StatusResponse(validated.environment().name(), true, validated.chainId(), chainId,
                clientVersion, validated.endpointHost(), Runtime.version().toString());
    }

    public record StatusResponse(String environment, boolean isSimulated, long validatedChainId, long liveChainId,
                                 String clientVersion, String endpointHost, String javaVersion) {
    }
}
