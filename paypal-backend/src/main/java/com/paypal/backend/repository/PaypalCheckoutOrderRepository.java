package com.paypal.backend.repository;

import com.paypal.backend.entity.PaypalCheckoutOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaypalCheckoutOrderRepository extends JpaRepository<PaypalCheckoutOrder, UUID> {
}
