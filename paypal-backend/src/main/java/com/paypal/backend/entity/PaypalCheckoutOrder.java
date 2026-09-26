package com.paypal.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "paypal_checkout_orders")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaypalCheckoutOrder {

    @Id
    @GeneratedValue
    UUID id;

    @Column(nullable = false)
    UUID payerUserId;

    @Column(nullable = false)
    UUID jobId;

    @Column(nullable = false, precision = 19, scale = 2)
    BigDecimal amountUsd;

    @Column(nullable = false, unique = true)
    String paypalOrderId;

    String paypalCaptureId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PaypalCheckoutOrderStatus status;

    @Column(nullable = false)
    LocalDateTime createdAt;

    LocalDateTime capturedAt;
}