package com.marketplace.backend.service;

import com.marketplace.backend.entity.FreelancerPayoutRecord;

public interface ClientPaymentService {
    void advance(FreelancerPayoutRecord payoutRecord);
}
