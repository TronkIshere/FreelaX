package com.misa.backend.entity;

import com.misa.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "incorrect_record_notification")
public class IncorrectRecordNotification extends AbstractEntity<UUID> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id", nullable = false)
    WithholdingCertificate certificate;

    @Column(name = "error_type", nullable = false)
    String errorType;

    @Column(name = "description")
    String description;

    @Column(name = "requested_action", nullable = false)
    String requestedAction;

    @Column(name = "next_action")
    String nextAction;
}
