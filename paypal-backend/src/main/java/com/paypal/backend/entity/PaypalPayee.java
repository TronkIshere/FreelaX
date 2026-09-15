package com.paypal.backend.entity;

import com.paypal.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "paypal_payee")
public class PaypalPayee extends AbstractEntity<UUID> {

    @Column(name = "user_id", nullable = false, unique = true)
    UUID userId;

    @Column(name = "full_name", nullable = false)
    String fullName;

    @Column(name = "paypal_email", nullable = false)
    String paypalEmail;

    @Column(name = "phone")
    String phone;

    @Column(name = "address")
    String address;

    @Column(name = "nationality")
    String nationality;

    @Column(name = "active", nullable = false)
    boolean active = true;
}
