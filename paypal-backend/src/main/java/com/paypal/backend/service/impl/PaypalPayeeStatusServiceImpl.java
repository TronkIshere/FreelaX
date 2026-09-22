package com.paypal.backend.service.impl;

import com.paypal.backend.dto.response.paypal.PaypalPayeeStatusResponse;
import com.paypal.backend.entity.PaypalPayee;
import com.paypal.backend.repository.PaypalPayeeRepository;
import com.paypal.backend.service.PaypalPayeeStatusService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalPayeeStatusServiceImpl implements PaypalPayeeStatusService {

    PaypalPayeeRepository paypalPayeeRepository;

    @Override
    public PaypalPayeeStatusResponse getStatus(UUID userId) {
        return paypalPayeeRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> PaypalPayeeStatusResponse.builder()
                        .payeeId(null)
                        .registered(false)
                        .active(false)
                        .build());
    }

    private PaypalPayeeStatusResponse toResponse(PaypalPayee payee) {
        return PaypalPayeeStatusResponse.builder()
                .payeeId(payee.getId())
                .registered(true)
                .active(payee.isActive())
                .build();
    }
}
