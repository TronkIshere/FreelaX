package com.freelax.avalanchegateway.controller;

import com.freelax.avalanchegateway.dto.response.StatusResponse;
import com.freelax.avalanchegateway.service.GatewayStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/avalanche")
public class StatusController {

    private final GatewayStatusService statusService;

    public StatusController(GatewayStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return statusService.status();
    }
}
