package com.paypal.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "paypal_checkout_orders")
@Getter
@Setter
public class PaypalCheckoutOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    UUID createdByUserId;

    UUID payeeId;

    String referenceId;

    BigDecimal amountUsd;

    String paypalOrderId;

    String paypalCaptureId;

    @Enumerated(EnumType.STRING)
    PaypalCheckoutOrderStatus status;

    LocalDateTime createdAt;

    LocalDateTime capturedAt;
}
