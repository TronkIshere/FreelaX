package com.misa.backend.controller;

import com.misa.backend.configuration.MisaProperties;
import com.misa.backend.dto.response.common.ResponseAPI;
import com.misa.backend.dto.response.misa.OrganizationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
@RequiredArgsConstructor
public class OrganizationController {

    private final MisaProperties misaProperties;

    @GetMapping
    public ResponseAPI<OrganizationResponse> get() {
        MisaProperties.Organization org = misaProperties.getOrganization();
        return ResponseAPI.<OrganizationResponse>builder()
                .code(200)
                .data(OrganizationResponse.builder()
                        .id(org.getId())
                        .name(org.getName())
                        .taxCode(org.getTaxCode())
                        .address(org.getAddress())
                        .phone(org.getPhone())
                        .taxAuthority(org.getTaxAuthority())
                        .status("ACTIVE")
                        .build())
                .build();
    }
}
