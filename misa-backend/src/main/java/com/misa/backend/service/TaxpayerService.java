package com.misa.backend.service;

import com.misa.backend.dto.request.misa.CreateTaxpayerRequest;
import com.misa.backend.dto.response.misa.TaxpayerResponse;

import java.util.UUID;

public interface TaxpayerService {

    TaxpayerResponse register(UUID userId, CreateTaxpayerRequest request);

    TaxpayerResponse registerForExternal(CreateTaxpayerRequest request);

    TaxpayerResponse getByUserId(UUID userId);

    TaxpayerResponse getById(UUID taxpayerId);
}