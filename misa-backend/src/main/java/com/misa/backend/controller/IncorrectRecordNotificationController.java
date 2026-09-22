package com.misa.backend.controller;

import com.misa.backend.dto.request.misa.IncorrectRecordNotificationRequest;
import com.misa.backend.dto.response.common.ResponseAPI;
import com.misa.backend.dto.response.misa.IncorrectRecordNotificationResponse;
import com.misa.backend.service.WithholdingCertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incorrect-record-notifications")
@RequiredArgsConstructor
public class IncorrectRecordNotificationController {

    private final WithholdingCertificateService withholdingCertificateService;

    @PostMapping
    public ResponseAPI<IncorrectRecordNotificationResponse> report(
            @Valid @RequestBody IncorrectRecordNotificationRequest request) {
        return ResponseAPI.<IncorrectRecordNotificationResponse>builder()
                .code(200)
                .message("Đã ghi nhận thông báo sai sót")
                .data(withholdingCertificateService.reportIncorrectRecord(request))
                .build();
    }
}
