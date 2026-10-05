package com.payment.backend.repository;

import com.payment.backend.entity.BofaRecipientCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface BofaRecipientCreditRepository extends JpaRepository<BofaRecipientCredit, UUID> {
    @Query("select coalesce(sum(c.amount), 0) from BofaRecipientCredit c "
            + "where c.recipientUserId = :recipient and c.currency = :currency")
    BigDecimal creditedEntitlement(@Param("recipient") UUID recipient, @Param("currency") String currency);
}
