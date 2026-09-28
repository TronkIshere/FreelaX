package com.marketplace.backend.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public enum TaxCertificateStatus {
    PENDING_EXPORT("Đang chờ lập chứng từ"),
    EXPORT_FAILED("Lập chứng từ thất bại"),
    DRAFT("Đã lập chứng từ, chưa phát hành"),
    SIGNED("Đã ký số"),
    SUBMITTING("Đã phát hành, đang gửi cơ quan thuế"),
    SUBMITTED("Đã gửi cơ quan thuế"),
    ACCEPTED("Cơ quan thuế đã chấp nhận"),
    REJECTED("Cơ quan thuế từ chối"),
    CORRECTION_REQUIRED("Cần điều chỉnh"),
    REPLACED("Đã được thay thế"),
    CANCELLED("Đã hủy");

    private static final Set<TaxCertificateStatus> SYNCABLE =
            EnumSet.of(DRAFT, SIGNED, SUBMITTING, SUBMITTED, CORRECTION_REQUIRED);

    private final String label;

    TaxCertificateStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isSyncable() {
        return SYNCABLE.contains(this);
    }

    public static Set<TaxCertificateStatus> syncableStatuses() {
        return EnumSet.copyOf(SYNCABLE);
    }

    public static Optional<TaxCertificateStatus> fromMisa(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            TaxCertificateStatus status = TaxCertificateStatus.valueOf(value.trim().toUpperCase());
            return status == PENDING_EXPORT || status == EXPORT_FAILED ? Optional.empty() : Optional.of(status);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}