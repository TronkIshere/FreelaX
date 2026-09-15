package com.paypal.backend.service.impl;

import com.paypal.backend.dto.request.paypal.CreatePaypalPayeeRequest;
import com.paypal.backend.dto.response.paypal.PaypalPayeeResponse;
import com.paypal.backend.entity.PaypalPayee;
import com.paypal.backend.exception.ApplicationException;
import com.paypal.backend.exception.ErrorCode;
import com.paypal.backend.repository.PaypalPayeeRepository;
import com.paypal.backend.service.PaypalPayeeService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalPayeeServiceImpl implements PaypalPayeeService {

    PaypalPayeeRepository paypalPayeeRepository;

    @Override
    @Transactional
    public PaypalPayeeResponse register(UUID userId, CreatePaypalPayeeRequest request) {
        PaypalPayee payee = paypalPayeeRepository.findByUserId(userId).orElseGet(PaypalPayee::new);
        payee.setUserId(userId);
        payee.setFullName(request.getFullName());
        payee.setPaypalEmail(request.getPaypalEmail());
        payee.setPhone(request.getPhone());
        payee.setAddress(request.getAddress());
        payee.setNationality(request.getNationality());
        payee.setActive(true);

        paypalPayeeRepository.save(payee);

        return toResponse(payee);
    }

    @Override
    public PaypalPayeeResponse getByUserId(UUID userId) {
        PaypalPayee payee = paypalPayeeRepository.findByUserId(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYEE_NOT_FOUND, userId));
        return toResponse(payee);
    }

    @Override
    public PaypalPayeeResponse getById(UUID payeeId) {
        PaypalPayee payee = paypalPayeeRepository.findById(payeeId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYEE_NOT_FOUND, payeeId));
        return toResponse(payee);
    }

    private PaypalPayeeResponse toResponse(PaypalPayee payee) {
        return PaypalPayeeResponse.builder()
                .id(payee.getId())
                .fullName(payee.getFullName())
                .paypalEmail(payee.getPaypalEmail())
                .phone(payee.getPhone())
                .address(payee.getAddress())
                .nationality(payee.getNationality())
                .active(payee.isActive())
                .build();
    }
}
