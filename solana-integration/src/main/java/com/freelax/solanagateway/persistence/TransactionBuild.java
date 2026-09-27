package com.freelax.solanagateway.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "solana_transaction_builds")
public class TransactionBuild {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "instruction_name", length = 64, nullable = false)
    private String instructionName;

    @Column(name = "message_hash", length = 64, nullable = false, unique = true)
    private String messageHash;

    @Column(name = "fee_payer", length = 44, nullable = false)
    private String feePayer;

    @Column(name = "required_signers", length = 512, nullable = false)
    private String requiredSigners;

    @Column(name = "recent_blockhash", length = 44, nullable = false)
    private String recentBlockhash;

    @Column(name = "last_valid_block_height", nullable = false)
    private long lastValidBlockHeight;

    @Column(length = 24, nullable = false)
    private String status;

    @Column(name = "submitted_signature", length = 88)
    private String submittedSignature;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionBuild() {
    }

    public TransactionBuild(String id, String instructionName, String messageHash, String feePayer,
                            String requiredSigners, String recentBlockhash,
                            long lastValidBlockHeight, Instant expiresAt) {
        this.id = id;
        this.instructionName = instructionName;
        this.messageHash = messageHash;
        this.feePayer = feePayer;
        this.requiredSigners = requiredSigners;
        this.recentBlockhash = recentBlockhash;
        this.lastValidBlockHeight = lastValidBlockHeight;
        this.status = "AWAITING_SIGNATURE";
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String id() { return id; }
    public String messageHash() { return messageHash; }
    public String instructionName() { return instructionName; }
    public String requiredSigners() { return requiredSigners; }
    public long lastValidBlockHeight() { return lastValidBlockHeight; }
    public Instant expiresAt() { return expiresAt; }
    public String submittedSignature() { return submittedSignature; }

    public void submitted(String signature) {
        this.status = "SUBMITTED";
        this.submittedSignature = signature;
        this.updatedAt = Instant.now();
    }
}
