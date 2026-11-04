package com.marketplace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** An EVM address bound to a user by a verified EIP-191 challenge (register A8, V7). Lower-case 0x address. */
@Getter
@Setter
@Entity
@Table(name = "evm_wallets")
public class EvmWallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    @Column(nullable = false, unique = true, length = 42)
    private String address;
    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;
}
