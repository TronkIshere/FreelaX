package com.payment.backend.repository;
import com.payment.backend.entity.BofaCheckoutRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface BofaCheckoutRefundRepository extends JpaRepository<BofaCheckoutRefund, UUID> {
    Optional<BofaCheckoutRefund> findByRefundKey(String refundKey);
    Optional<BofaCheckoutRefund> findByCheckoutOrderId(UUID checkoutOrderId);
}
