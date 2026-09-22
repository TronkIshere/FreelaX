package com.misa.backend.service.impl;

import com.misa.backend.dto.request.misa.CreateTaxpayerRequest;
import com.misa.backend.dto.response.misa.TaxpayerResponse;
import com.misa.backend.entity.Taxpayer;
import com.misa.backend.exception.ApplicationException;
import com.misa.backend.exception.ErrorCode;
import com.misa.backend.repository.TaxpayerRepository;
import com.misa.backend.service.TaxpayerService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxpayerServiceImpl implements TaxpayerService {

    TaxpayerRepository taxpayerRepository;

    @Override
    @Transactional
    public TaxpayerResponse register(UUID userId, CreateTaxpayerRequest request) {
        if (StringUtils.hasText(request.getTaxCode()) && taxpayerRepository.existsByTaxCode(request.getTaxCode())) {
            throw new ApplicationException(ErrorCode.TAX_CODE_ALREADY_EXISTS, request.getTaxCode());
        }

        Taxpayer taxpayer = taxpayerRepository.findByUserId(userId).orElseGet(Taxpayer::new);
        taxpayer.setUserId(userId);
        taxpayer.setFullName(request.getFullName());
        taxpayer.setAddress(request.getAddress());
        taxpayer.setPhone(request.getPhone());
        taxpayer.setTaxCode(request.getTaxCode());
        taxpayer.setIdentityNumber(request.getIdentityNumber());
        taxpayer.setNationality(request.getNationality());
        taxpayer.setActive(true);

        taxpayerRepository.save(taxpayer);

        return toResponse(taxpayer);
    }

    @Override
    public TaxpayerResponse getByUserId(UUID userId) {
        Taxpayer taxpayer = taxpayerRepository.findByUserId(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TAXPAYER_NOT_FOUND, userId));
        return toResponse(taxpayer);
    }

    @Override
    public TaxpayerResponse getById(UUID taxpayerId) {
        Taxpayer taxpayer = taxpayerRepository.findById(taxpayerId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TAXPAYER_NOT_FOUND, taxpayerId));
        return toResponse(taxpayer);
    }

    private TaxpayerResponse toResponse(Taxpayer taxpayer) {
        return TaxpayerResponse.builder()
                .id(taxpayer.getId())
                .fullName(taxpayer.getFullName())
                .address(taxpayer.getAddress())
                .phone(taxpayer.getPhone())
                .taxCode(taxpayer.getTaxCode())
                .identityNumber(taxpayer.getIdentityNumber())
                .nationality(taxpayer.getNationality())
                .active(taxpayer.isActive())
                .build();
    }
}
