package com.misa.backend.controller;

import com.misa.backend.dto.request.misa.CancelCertificateRequest;
import com.misa.backend.dto.request.misa.CreateWithholdingCertificateRequest;
import com.misa.backend.dto.request.misa.IssueCertificateRequest;
import com.misa.backend.dto.request.misa.SubmitCertificateRequest;
import com.misa.backend.dto.response.common.ResponseAPI;
import com.misa.backend.dto.response.misa.CertificateCancelResponse;
import com.misa.backend.dto.response.misa.CertificateIssueResponse;
import com.misa.backend.dto.response.misa.CertificateLookupResponse;
import com.misa.backend.dto.response.misa.CertificateStatusResponse;
import com.misa.backend.dto.response.misa.CertificateSubmitResponse;
import com.misa.backend.dto.response.misa.WithholdingCertificateResponse;
import com.misa.backend.service.WithholdingCertificateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/withholding-certificates")
@RequiredArgsConstructor
public class WithholdingCertificateController {

    private final WithholdingCertificateService withholdingCertificateService;

    @PostMapping
    public ResponseAPI<WithholdingCertificateResponse> create(@Valid @RequestBody CreateWithholdingCertificateRequest request) {
        return ResponseAPI.<WithholdingCertificateResponse>builder()
                .code(200)
                .message("Tạo chứng từ khấu trừ thành công")
                .data(withholdingCertificateService.create(request))
                .build();
    }

    @PostMapping("/{id}/issue")
    public ResponseAPI<CertificateIssueResponse> issue(@PathVariable UUID id,
                                                         @Valid @RequestBody IssueCertificateRequest request) {
        return ResponseAPI.<CertificateIssueResponse>builder()
                .code(200)
                .message("Ký và phát hành chứng từ thành công")
                .data(withholdingCertificateService.issue(id, request))
                .build();
    }

    @PostMapping("/{id}/submit")
    public ResponseAPI<CertificateSubmitResponse> submit(@PathVariable UUID id,
                                                           @Valid @RequestBody SubmitCertificateRequest request) {
        return ResponseAPI.<CertificateSubmitResponse>builder()
                .code(200)
                .message("Gửi chứng từ thành công")
                .data(withholdingCertificateService.submit(id, request))
                .build();
    }

    @GetMapping("/{id}/status")
    public ResponseAPI<CertificateStatusResponse> status(@PathVariable UUID id) {
        return ResponseAPI.<CertificateStatusResponse>builder()
                .code(200)
                .data(withholdingCertificateService.getStatus(id))
                .build();
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @ResponseBody
    public byte[] pdf(@PathVariable UUID id) {
        return withholdingCertificateService.getPdf(id);
    }

    @GetMapping(value = "/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String xml(@PathVariable UUID id) {
        return withholdingCertificateService.getXml(id);
    }

    @GetMapping("/lookup/{lookupCode}")
    public ResponseAPI<CertificateLookupResponse> lookup(@PathVariable String lookupCode) {
        return ResponseAPI.<CertificateLookupResponse>builder()
                .code(200)
                .data(withholdingCertificateService.lookup(lookupCode))
                .build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseAPI<CertificateCancelResponse> cancel(@PathVariable UUID id,
                                                           @Valid @RequestBody CancelCertificateRequest request) {
        return ResponseAPI.<CertificateCancelResponse>builder()
                .code(200)
                .message("Hủy chứng từ thành công")
                .data(withholdingCertificateService.cancel(id, request))
                .build();
    }
}
