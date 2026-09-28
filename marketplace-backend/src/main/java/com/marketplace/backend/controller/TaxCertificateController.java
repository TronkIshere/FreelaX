package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.tax.TaxCertificateFile;
import com.marketplace.backend.dto.response.tax.TaxCertificateResponse;
import com.marketplace.backend.service.TaxCertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace/tax-records")
@RequiredArgsConstructor
public class TaxCertificateController {

    private final TaxCertificateService taxCertificateService;

    @GetMapping
    public ResponseAPI<PageResponse<TaxCertificateResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "10") int size) {
        return ResponseAPI.<PageResponse<TaxCertificateResponse>>builder()
                .code(200)
                .data(taxCertificateService.listForUser(principal.getId(), page, size))
                .build();
    }

    @GetMapping("/{taxRecordId}")
    public ResponseAPI<TaxCertificateResponse> getById(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID taxRecordId) {
        return ResponseAPI.<TaxCertificateResponse>builder()
                .code(200)
                .data(taxCertificateService.getForParticipant(principal.getId(), taxRecordId))
                .build();
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseAPI<TaxCertificateResponse> getByJob(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable UUID jobId) {
        return ResponseAPI.<TaxCertificateResponse>builder()
                .code(200)
                .data(taxCertificateService.getByJobForParticipant(principal.getId(), jobId))
                .build();
    }

    @PostMapping("/{taxRecordId}/sync")
    public ResponseAPI<TaxCertificateResponse> sync(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID taxRecordId) {
        return ResponseAPI.<TaxCertificateResponse>builder()
                .code(200)
                .message("Đã đồng bộ trạng thái chứng từ")
                .data(taxCertificateService.syncForParticipant(principal.getId(), taxRecordId))
                .build();
    }

    @PostMapping("/{taxRecordId}/retry-export")
    public ResponseAPI<TaxCertificateResponse> retryExport(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable UUID taxRecordId) {
        return ResponseAPI.<TaxCertificateResponse>builder()
                .code(200)
                .message("Đã lập lại chứng từ")
                .data(taxCertificateService.retryExportForParticipant(principal.getId(), taxRecordId))
                .build();
    }

    @GetMapping("/{taxRecordId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID taxRecordId,
                                              @RequestParam(defaultValue = "false") boolean inline) {
        return toFileResponse(taxCertificateService.downloadPdfForParticipant(principal.getId(), taxRecordId),
                MediaType.APPLICATION_PDF, inline);
    }

    @GetMapping("/{taxRecordId}/xml")
    public ResponseEntity<byte[]> downloadXml(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID taxRecordId,
                                              @RequestParam(defaultValue = "false") boolean inline) {
        return toFileResponse(taxCertificateService.downloadXmlForParticipant(principal.getId(), taxRecordId),
                MediaType.APPLICATION_XML, inline);
    }

    private ResponseEntity<byte[]> toFileResponse(TaxCertificateFile file, MediaType mediaType, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(file.content().length)
                .body(file.content());
    }
}