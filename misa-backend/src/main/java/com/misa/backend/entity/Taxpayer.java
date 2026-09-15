package com.misa.backend.entity;

import com.misa.backend.entity.common.AbstractEntity;
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
@Table(name = "taxpayer")
public class Taxpayer extends AbstractEntity<UUID> {

    @Column(name = "user_id", nullable = false, unique = true)
    UUID userId;

    @Column(name = "full_name", nullable = false)
    String fullName;

    @Column(name = "address")
    String address;

    @Column(name = "phone")
    String phone;

    @Column(name = "tax_code", unique = true)
    String taxCode;

    @Column(name = "identity_number", nullable = false)
    String identityNumber;

    @Column(name = "nationality", nullable = false)
    String nationality;

    @Column(name = "active", nullable = false)
    boolean active = true;
}
