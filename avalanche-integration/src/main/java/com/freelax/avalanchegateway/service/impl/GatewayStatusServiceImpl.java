package com.freelax.avalanchegateway.service.impl;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.dto.response.ScanStatusResponse;
import com.freelax.avalanchegateway.dto.response.StatusResponse;
import com.freelax.avalanchegateway.scan.LogScanner;
import com.freelax.avalanchegateway.scan.ScanCoordinator;
import com.freelax.avalanchegateway.service.GatewayStatusService;
import com.freelax.avalanchegateway.startup.EnvironmentStartupValidator;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class GatewayStatusServiceImpl implements GatewayStatusService {

    private static final Logger log = LoggerFactory.getLogger(GatewayStatusServiceImpl.class);

    private final ChainClient chainClient;
    private final EnvironmentStartupValidator validator;
    private final ObjectProvider<ScanCoordinator> coordinator;
    private final ObjectProvider<LogScanner> scanner;

    public GatewayStatusServiceImpl(ChainClient chainClient, EnvironmentStartupValidator validator,
                                    ObjectProvider<ScanCoordinator> coordinator, ObjectProvider<LogScanner> scanner) {
        this.chainClient = chainClient;
        this.validator = validator;
        this.coordinator = coordinator;
        this.scanner = scanner;
    }

    @Override
    public StatusResponse status() {
        ValidatedEnvironment validated = validator.validated();
        long chainId = chainClient.chainId();
        String clientVersion = chainClient.clientVersion();
        return new StatusResponse(validated.environment().name(), true, validated.chainId(), chainId,
                clientVersion, validated.endpointHost(), Runtime.version().toString());
    }

    @Override
    public ScanStatusResponse scanStatus() {
        ScanCoordinator scanCoordinator = coordinator.getIfAvailable();
        LogScanner logScanner = scanner.getIfAvailable();
        if (scanCoordinator == null || logScanner == null) {
            return new ScanStatusResponse("DISABLED", 0, 0, null, null, null, null, null);
        }
        ScanCoordinator.Health health = scanCoordinator.health();
        Long cursor = null;
        Long head = null;
        try {
            cursor = logScanner.cursorNow().orElse(null);
            head = logScanner.safeHeadNow();
        } catch (RuntimeException e) {
            log.warn("scan position could not be read; reporting it as unknown: {}", e.getClass().getSimpleName());
        }
        Long lag = cursor == null || head == null ? null : Math.max(0, head - cursor);
        return new ScanStatusResponse(health.state(), health.passes(), health.consecutiveFailures(), health.lastErrorClass(),
                health.lastSuccessAtMillis(), cursor, head, lag);
    }
}
