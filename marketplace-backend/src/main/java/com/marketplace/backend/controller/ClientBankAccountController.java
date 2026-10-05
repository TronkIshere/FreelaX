package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.entity.BankCode;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment-methods/bank-account")
@RequiredArgsConstructor
public class ClientBankAccountController {
    private final UserRepository users;

    @GetMapping
    public ResponseAPI<BankAccountSummary> get(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseAPI.<BankAccountSummary>builder().code(200).data(summary(client(principal.getId()))).build();
    }

    @PutMapping
    @Transactional
    public ResponseAPI<BankAccountSummary> put(@AuthenticationPrincipal UserPrincipal principal,
                                                @Valid @RequestBody BankAccountRequest request) {
        User client = client(principal.getId());
        client.setBankCode(request.getBankCode());
        client.setBankAccountNumber(request.getBankAccountNumber().trim());
        client.setBankAccountHolderName(request.getBankAccountHolderName().trim());
        return ResponseAPI.<BankAccountSummary>builder().code(200).data(summary(users.save(client))).build();
    }

    private User client(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_EXISTED));
        if (user.getUserType() != UserType.CLIENT) throw new ApplicationException(ErrorCode.NOT_A_CLIENT);
        return user;
    }

    private BankAccountSummary summary(User user) {
        String number = user.getBankAccountNumber();
        boolean ready = user.getBankCode() != null && number != null && !number.isBlank()
                && user.getBankAccountHolderName() != null && !user.getBankAccountHolderName().isBlank();
        String masked = ready ? "••••" + number.substring(Math.max(0, number.length() - 4)) : null;
        return new BankAccountSummary("BANK_ACCOUNT_ON_FILE", ready,
                ready ? user.getBankCode().name() : null, masked);
    }

    public record BankAccountSummary(String paymentMethodId, boolean ready, String bankCode,
                                     String maskedAccountNumber) { }

    @Getter
    @Setter
    public static class BankAccountRequest {
        @NotNull
        private BankCode bankCode;
        @NotBlank
        @Pattern(regexp = "[0-9]{6,34}")
        private String bankAccountNumber;
        @NotBlank
        @Size(min = 2, max = 255)
        private String bankAccountHolderName;
    }
}
